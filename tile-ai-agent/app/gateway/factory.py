"""按配置组装网关 —— 厂商可插拔的唯一入口(架构 §7.2)。

§7.2:「通过配置 `model.llm.provider` / `model.image.provider` /
`model.embedding.provider` 选择实现」。文档里的点号路径是**记号**,
落到本服务就是环境变量 `MODEL_LLM_PROVIDER`;两者的对应关系只在本文件里,
不散落到别处。

**未知 provider 一律抛 `GatewayConfigError`,不静默降级。**
把 `deepseek` 拼成 `deepsek` 却照常返回一个"能用的"网关,是最难查的一类故障。

**Redis 不可用时限流降级放行**,只记一条告警(架构 §13.3「降级不阻断主流程」)。
限流保护的是下游模型额度,不是本服务的可用性 —— 为了限流把问答打挂是本末倒置。
"""

from __future__ import annotations

import logging
import random
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

from app.gateway.errors import GatewayConfigError
from app.gateway.dashscope_image import DashScopeImageGen
from app.gateway.openai_compat import OpenAICompatibleLLM
from app.gateway.ports import EmbeddingGateway, ImageGenGateway, LLMGateway
from app.gateway.siliconflow_image import SiliconFlowImageGen
from app.gateway.resilience import (
    CallGuard,
    CircuitBreaker,
    CostMeter,
    FailureRateTracker,
    RedisTokenBucket,
    RetryPolicy,
)
from app.gateway.resilient import (
    ResilientImageGenGateway,
    ResilientLLMGateway,
)

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ProviderPreset:
    """厂商预置:默认端点与默认模型 ID。

    `default_model` 留空的厂商表示**模型 ID 尚未确定** —— 此时必须由配置显式给出,
    不替它猜。智谱 GLM 就是这种情形:架构 §15.1 定了厂商,没定型号。
    """

    base_url: str
    default_model: str = ""


_LLM_PRESETS: dict[str, ProviderPreset] = {
    "deepseek": ProviderPreset("https://api.deepseek.com", "deepseek-flash"),
    # ⚠️ 模型 ID 待核对官方文档 —— 不填默认值,配置没给就报错。
    "zhipu": ProviderPreset("https://open.bigmodel.cn/api/paas/v4"),
}

_IMAGE_PRESETS: dict[str, ProviderPreset] = {
    # ⚠️ **不是** OpenAI 兼容入口(`/compatible-mode/v1`)—— 图像走 DashScope 原生协议。
    # 2026-09-29 用真实密钥实测确认,详见 app/gateway/dashscope_image.py。
    "dashscope": ProviderPreset("https://dashscope.aliyuncs.com", "wan2.7-image-pro"),
    # 硅基流动。端点路径是 OpenAI images 风格的 `/v1/images/generations`,
    # 但**参数是它自己的扩展**(参考图字段叫 `image` / `image2` …,**不是** `imageUpload`)
    # —— 2026-10-02 用真实密钥实测确认,详见 app/gateway/siliconflow_image.py。
    "siliconflow": ProviderPreset("https://api.siliconflow.cn", "Qwen/Qwen-Image-Edit-2509"),
}

#: Embedding 的预置。
#:
#: **云端厂商仍然留空** —— 那几家的模型 ID 要按官方文档填,编一个等于把没做的决策
#: 假装做掉了(P0-2 残留)。这里只有**本地 Ollama**:端点固定、模型是公开的开源权重
#: (Apache 2.0),不存在"选型未定"的问题。
#:
#: ⚠️ 换这个模型时**必须全量重建索引** —— 向量语义变了,旧新混在同一个集合里,
#: 检索结果是乱的(§9.1 / §15.2)。`OllamaEmbedding` 会在维度对不上时当场报错。
_EMBEDDING_PRESETS: dict[str, ProviderPreset] = {
    "ollama": ProviderPreset("http://localhost:11434", "qwen3-embedding:0.6b"),
}

#: 进程内跑本地模型时的默认模型目录。
#:
#: ⚠️ **相对于本仓库的 `tile-ai-agent/`,不是当前工作目录** —— 用相对路径会踩
#: "从哪个目录跑就读哪个目录"的坑(`tools/rebuild_index.py` 那条已经踩过一次)。
_LOCAL_EMBEDDING_DEFAULT_MODEL = str(
    Path(__file__).resolve().parents[2] / "models" / "Qwen3-Embedding-0.6B"
)


def build_llm_gateway(
    settings: Any = None,
    *,
    redis_client: Any = None,
    client: Any = None,
    clock: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
    jitter_source: Callable[[], float] = random.random,
    meter: CostMeter | None = None,
    tracker: FailureRateTracker | None = None,
) -> LLMGateway:
    """组装 LLM 网关;配了备份供应商则返回带故障切换的实例(§13.7 / §15.1)。"""
    config = _config(settings)
    if not config.model_llm_provider:
        raise GatewayConfigError(
            "MODEL_LLM_PROVIDER 未配置 —— 见 tile-ai-agent/.env.example"
        )

    shared_meter = meter if meter is not None else CostMeter(config.gateway_model_prices)
    shared_tracker = (
        tracker if tracker is not None else FailureRateTracker(window=config.gateway_failover_window)
    )

    primary = _build_llm_provider(
        config,
        provider=config.model_llm_provider,
        api_key=config.model_llm_api_key,
        base_url=config.model_llm_base_url,
        model=config.model_llm_model,
        redis_client=redis_client,
        client=client,
        clock=clock,
        sleep=sleep,
        jitter_source=jitter_source,
        meter=shared_meter,
        tracker=shared_tracker,
    )

    if not config.model_llm_backup_provider:
        return primary

    backup = _build_llm_provider(
        config,
        provider=config.model_llm_backup_provider,
        api_key=config.model_llm_backup_api_key,
        base_url=config.model_llm_backup_base_url,
        model=config.model_llm_backup_model,
        redis_client=redis_client,
        client=client,
        clock=clock,
        sleep=sleep,
        jitter_source=jitter_source,
        meter=shared_meter,
        tracker=shared_tracker,
    )

    # 延迟导入:failover 依赖 resilient,而 resilient 被本模块用到 ——
    # 顶部导入也不会成环,但放在这里能让"只有在真的配了备份时才需要它"更直白。
    from app.gateway.failover import FailoverLLMGateway

    return FailoverLLMGateway(
        primary=primary,
        backup=backup,
        primary_name=config.model_llm_provider,
        backup_name=config.model_llm_backup_provider,
        tracker=shared_tracker,
        threshold=config.gateway_failover_failure_rate,
        min_samples=config.gateway_failover_min_samples,
        probe_seconds=config.gateway_failover_probe_seconds,
        clock=clock,
    )


def build_image_gateway(
    settings: Any = None,
    *,
    redis_client: Any = None,
    client: Any = None,
    clock: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
    jitter_source: Callable[[], float] = random.random,
    meter: CostMeter | None = None,
) -> ImageGenGateway:
    """组装图像网关。"""
    config = _config(settings)
    provider = config.model_image_provider
    if not provider:
        raise GatewayConfigError(
            "MODEL_IMAGE_PROVIDER 未配置 —— 见 tile-ai-agent/.env.example"
        )

    if provider == "placeholder":
        # §29 阶段性收缩:本期用替身跑通端到端,不做出图质量评估。
        # 它**不是预置表里的一项** —— 预置表列的是真实厂商,
        # 而替身没有 base_url/密钥/模型 ID 的概念,混进去会让那张表失去意义。
        # 切回真模型只需把 MODEL_IMAGE_PROVIDER 改成 dashscope(见 §29.5)。
        from app.gateway.placeholder_image import PlaceholderImageGen

        logger.warning(
            "图像网关使用**替身**(MODEL_IMAGE_PROVIDER=placeholder):"
            "出图结果是固定占位图,不代表真实效果。上线前必须切回真实模型(架构 §29)"
        )
        return PlaceholderImageGen()

    preset = _IMAGE_PRESETS.get(provider)
    if preset is None:
        raise GatewayConfigError(
            f"未知的图像 provider:{provider}。已支持:{sorted(_IMAGE_PRESETS)}"
        )

    adapter = _build_image_adapter(
        provider=provider,
        preset=preset,
        config=config,
        client=client,
    )
    return ResilientImageGenGateway(
        adapter,
        guard=_guard(
            config,
            provider,
            redis_client=redis_client,
            clock=clock,
            sleep=sleep,
            jitter_source=jitter_source,
        ),
        provider=provider,
        meter=meter if meter is not None else CostMeter(config.gateway_model_prices),
    )


def _build_image_adapter(
    *,
    provider: str,
    preset: ProviderPreset,
    config: Any,
    client: Any = None,
) -> Any:
    """按 provider 选真实的图像适配器。

    密钥**按 provider 取**:硅基流动优先用 `MODEL_IMAGE_API_KEY_SILICONFLOW`,
    留空则回退到通用的 `MODEL_IMAGE_API_KEY`。

    这样"演示用硅基流动、答辩用万相"时,两个密钥可以**同时留在 .env 里**,
    靠 `MODEL_IMAGE_PROVIDER` 切 —— 不用来回改写,也不会把另一个覆盖掉。
    """
    api_key = _secret(config.model_image_api_key)
    if provider == "siliconflow":
        api_key = _secret(config.model_image_api_key_siliconflow) or api_key

    common: dict[str, Any] = {
        "provider": provider,
        "base_url": config.model_image_base_url or preset.base_url,
        "model": config.model_image_model or preset.default_model,
        "api_key": api_key,
        # 图像单图 ≤60s 是架构 §7.3 / §13.1 的硬指标
        "timeout_seconds": config.gateway_image_timeout_seconds,
        "client": client,
    }
    if provider == "siliconflow":
        return SiliconFlowImageGen(**common)
    return DashScopeImageGen(**common)


def build_embedding_gateway(settings: Any = None) -> EmbeddingGateway:
    """组装 Embedding 网关。

    **留空 = 确定性占位实现**(T-0.13 的产物):索引链路照常可跑、可测,
    但**没有语义能力** —— "浅灰"和"灰白"在它眼里毫无关系,检索能跑但挑不准。

    填了 provider 就用真的。目前只支持 **`ollama`**(本地开源模型,见
    `app/gateway/ollama_embedding.py`);云端厂商的模型选型(P0-2)仍未定,
    不在这里编预设。

    ⚠️ **从占位换成真模型必须全量重建索引** —— 向量语义变了,旧新混在一个集合里
    检索结果是乱的。重建用 `tools/rebuild_index.py`(它读这里的配置,不会再写死占位)。
    """
    config = _config(settings)
    provider = (config.model_embedding_provider or "").strip()

    if provider in ("", "placeholder"):
        # 占位实现在 app/index/embedding.py —— T-0.13 的产物,不改动它。
        # 它与本模块 EmbeddingGateway 协议结构一致,故可直接互换。
        from app.index.embedding import DeterministicPlaceholderEmbedding

        return DeterministicPlaceholderEmbedding(config.index_vector_size)

    preset = _EMBEDDING_PRESETS.get(provider)
    if preset is None and provider != "local":
        raise GatewayConfigError(
            f"未知的 Embedding provider:{provider}。"
            f"已支持:{['placeholder', 'local'] + sorted(_EMBEDDING_PRESETS)}"
            f"(云端厂商模型选型属 P0-2 残留,定案后再接)"
        )

    if provider == "ollama":
        from app.gateway.ollama_embedding import OllamaEmbedding

        return OllamaEmbedding(
            base_url=config.model_embedding_base_url or preset.base_url,
            model=config.model_embedding_model or preset.default_model,
            # 维度取 `index_vector_size` —— 集合就是按它建的。两边必须一致,
            # 对不上时适配器会当场报错(而不是写出一堆维度错的向量)。
            dimension=config.index_vector_size,
            timeout_seconds=config.gateway_embedding_timeout_seconds,
        )

    if provider == "local":
        from app.gateway.local_embedding import LocalEmbedding

        return LocalEmbedding(
            model=config.model_embedding_model or _LOCAL_EMBEDDING_DEFAULT_MODEL,
            dimension=config.index_vector_size,
            # **不自动挑设备**:自动挑会让"换个机器行为就变"(有 GPU 的走 GPU、
            # 没有的走 CPU,两边若有一丁点数值差异,检索结果就会飘)。显式配置。
            device=config.embedding_device,
        )

    raise GatewayConfigError(f"Embedding provider「{provider}」尚未实现")


# ----------------------------------------------------------------------

def _build_llm_provider(
    config: Any,
    *,
    provider: str,
    api_key: Any,
    base_url: str,
    model: str,
    redis_client: Any,
    client: Any,
    clock: Callable[[], float],
    sleep: Callable[[float], None],
    jitter_source: Callable[[], float],
    meter: CostMeter,
    tracker: FailureRateTracker,
) -> ResilientLLMGateway:
    preset = _LLM_PRESETS.get(provider)
    if preset is None:
        raise GatewayConfigError(
            f"未知的 LLM provider:{provider}。已支持:{sorted(_LLM_PRESETS)}"
        )
    adapter = OpenAICompatibleLLM(
        provider=provider,
        base_url=base_url or preset.base_url,
        model=model or preset.default_model,
        api_key=_secret(api_key),
        timeout_seconds=config.gateway_llm_timeout_seconds,
        client=client,
    )
    return ResilientLLMGateway(
        adapter,
        guard=_guard(
            config,
            provider,
            redis_client=redis_client,
            clock=clock,
            sleep=sleep,
            jitter_source=jitter_source,
            tracker=tracker,
        ),
        provider=provider,
        meter=meter,
    )


def _guard(
    config: Any,
    provider: str,
    *,
    redis_client: Any,
    clock: Callable[[], float],
    sleep: Callable[[float], None],
    jitter_source: Callable[[], float],
    tracker: FailureRateTracker | None = None,
) -> CallGuard:
    """每个供应商一套独立的熔断与限流。

    熔断**必须按供应商分开**:主力挂了不该让备份的熔断器跟着打开 ——
    那样备份就永远接不住。
    """
    return CallGuard(
        provider=provider,
        retry=RetryPolicy(
            max_attempts=config.gateway_max_attempts,
            base_delay_seconds=config.gateway_backoff_base_seconds,
            max_delay_seconds=config.gateway_backoff_max_seconds,
        ),
        rate_limiter=_rate_limiter(config, provider, redis_client),
        breaker=CircuitBreaker(
            failure_threshold=config.gateway_circuit_failure_threshold,
            recovery_seconds=config.gateway_circuit_recovery_seconds,
            clock=clock,
        ),
        tracker=tracker,
        sleep=sleep,
        jitter_source=jitter_source,
    )


def _rate_limiter(
    config: Any, provider: str, redis_client: Any
) -> RedisTokenBucket | None:
    client = redis_client if redis_client is not None else _connect_redis(config.redis_url)
    if client is None:
        # 降级放行:限流失效好过问答不可用(架构 §13.3)
        logger.warning(
            "Redis 不可用,限流降级放行 provider=%s —— 下游额度将不受本地保护", provider
        )
        return None
    return RedisTokenBucket(
        client,
        # 按供应商分开计数:主力与备份是两份独立额度
        key=f"gateway:ratelimit:{provider}",
        rate_per_second=config.gateway_rate_limit_rps,
        burst=config.gateway_rate_limit_burst,
    )


def _connect_redis(url: str) -> Any:
    """连 Redis。连不上返回 None(由调用方决定降级),不抛。

    延迟 import:没有 Redis 的环境(或只跑纯单测的环境)不该因为
    import 这个模块就失败。
    """
    try:
        import redis

        client = redis.Redis.from_url(
            url, socket_connect_timeout=2, socket_timeout=2, decode_responses=True
        )
        client.ping()
        return client
    except Exception as error:  # noqa: BLE001 —— 连不上就是连不上,原因不重要
        logger.warning("连接 Redis 失败(%s):%s", url, type(error).__name__)
        return None


def _secret(value: Any) -> str:
    """取出密钥明文。同时容忍 `SecretStr` 与普通字符串(测试里用得到)。"""
    if value is None:
        return ""
    getter = getattr(value, "get_secret_value", None)
    if callable(getter):
        return getter()
    return str(value)


def _config(settings: Any) -> Any:
    if settings is not None:
        return settings
    from app.core.config import settings as default_settings

    return default_settings
