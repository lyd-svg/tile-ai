"""服务配置。

密钥一律经环境变量注入,**不落代码、不落库**(架构 §13.6 / §12.3)。
本文件只声明字段与默认值,任何真实密钥都不得出现在这里。

**密钥字段用 `SecretStr`**:`repr()` 只会打印 `**********`,
于是"日志脱敏"(§13.6)是**结构性保证**,不靠编码纪律 —— 谁不小心把
`settings` 打进日志,也不会泄密。取值须显式 `.get_secret_value()`。

配置键命名说明:架构 §7.2 写的是 `model.llm.provider` 这类**点号路径**,
那是文档记号;落到本服务就是环境变量 `MODEL_LLM_PROVIDER`。两者的对应关系
集中在 `app/gateway/factory.py` 里,不散落各处。
"""

from __future__ import annotations

from pydantic import BaseModel, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class ModelPrice(BaseModel):
    """单价表(成本计量用,架构 §7.2)。

    **单价只从配置来,不硬编码** —— 厂商调价是常态(DeepSeek 就调过多次),
    写死在代码里会让成本账目悄悄失真。
    单位:每百万 token 的人民币价,与每一张图的单价。
    """

    input_per_million: float = 0.0
    output_per_million: float = 0.0
    per_image: float = 0.0


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    service_name: str = "tile-ai-agent"
    port: int = 8000

    # Java 业务核 —— 同步 REST 调用目标(架构 §6.1)
    java_base_url: str = "http://localhost:8080"

    # 服务间鉴权:请求头 X-Service-Token(openapi-common.yaml#/components/securitySchemes)
    service_token: SecretStr = SecretStr("")

    # --- Python → Java 取数与回传(T-1.11)---
    # 取数:一次索引查询,几秒不回就是出问题了。
    java_api_timeout_seconds: float = 5.0
    # 回传:Java 要写任务状态、终局还要顺带保存方案(一次事务),给更宽的余量。
    # 两者都与 `gateway_image_timeout_seconds`(60s)无关 —— 那是**模型**调用的硬指标。
    java_callback_timeout_seconds: float = 10.0

    # ------------------------------------------------------------------
    # 模型网关(架构 §7.1 / §7.2;选型见 §15.1)
    # ------------------------------------------------------------------
    # provider 取值见 app/gateway/factory.py 的预置表;留空表示未配置。
    model_llm_provider: str = ""
    model_llm_api_key: SecretStr = SecretStr("")
    model_llm_base_url: str = ""
    model_llm_model: str = ""

    # 备份供应商 —— 架构 §15.1 要求「主力 + 备份双供应商」,
    # §13.7 规定失败率 > 10% 时切换。
    model_llm_backup_provider: str = ""
    model_llm_backup_api_key: SecretStr = SecretStr("")
    model_llm_backup_base_url: str = ""
    model_llm_backup_model: str = ""

    model_image_provider: str = ""
    model_image_api_key: SecretStr = SecretStr("")
    model_image_base_url: str = ""
    model_image_model: str = ""
    #: 硅基流动专用的图像密钥。
    #:
    #: **为什么要单开一个而不是共用 `model_image_api_key`**:厂商不同、密钥不同,
    #: 而"两个都留着、靠 `MODEL_IMAGE_PROVIDER` 切"是明确的使用方式
    #: (演示用硅基流动、答辩用万相)。共用一个变量就得来回改写、还会把另一个密钥
    #: 覆盖掉。留空时**回退到 `model_image_api_key`**,所以只配一个也能跑。
    model_image_api_key_siliconflow: SecretStr = SecretStr("")

    model_embedding_provider: str = ""
    model_embedding_api_key: SecretStr = SecretStr("")
    model_embedding_base_url: str = ""
    model_embedding_model: str = ""
    #: 本地嵌入模型跑在哪。**不自动挑**:自动挑会让"换个机器行为就变"
    #: (有 GPU 走 GPU、没有走 CPU,数值上的一点差异会让检索结果飘)。
    embedding_device: str = "cpu"

    # 成本计量单价表,形如 {"deepseek": {"input_per_million": 1, "output_per_million": 2}}
    gateway_model_prices: dict[str, ModelPrice] = {}

    # ------------------------------------------------------------------
    # 韧性旋钮(架构 §7.2「统一封装:超时、重试、限流、熔断、成本计量」)
    # ------------------------------------------------------------------
    # 超时:图像 60s 是架构 §7.3 / §13.1 的**硬指标**(单图 ≤60s),不可放宽。
    gateway_llm_timeout_seconds: float = 30.0
    gateway_image_timeout_seconds: float = 60.0
    # Embedding:本地推理(如 Ollama 跑 0.6B 的小模型),一次几十毫秒到几秒。
    # 给 15s 是"本机服务没起/卡住"时的告警线,不是性能指标。
    gateway_embedding_timeout_seconds: float = 15.0

    gateway_max_attempts: int = 3
    gateway_backoff_base_seconds: float = 0.5
    gateway_backoff_max_seconds: float = 8.0

    # 限流:架构 §13.2 明文指定用 **Redis 令牌桶**保护下游模型额度。
    gateway_rate_limit_rps: float = 5.0
    gateway_rate_limit_burst: int = 10
    # Redis 连不上时限流失效(降级放行)而不是把请求打挂 —— 架构 §13.3
    # 「模型不可用时降级不阻断主流程」。限流保护的是下游额度,不是本服务可用性。
    redis_url: str = "redis://localhost:6379/0"

    gateway_circuit_failure_threshold: int = 5
    gateway_circuit_recovery_seconds: float = 30.0

    # §13.7「模型调用失败率 > 10%(触发切换备份供应商)」
    gateway_failover_failure_rate: float = 0.10
    # 样本太少时不下判定 —— 否则第一次调用失败(1/1 = 100%)就会切走。
    gateway_failover_min_samples: int = 5
    gateway_failover_window: int = 20
    # 切到备份后,每隔这么久放一个真实请求去主供应商试探(半开)
    gateway_failover_probe_seconds: float = 60.0

    # Qdrant —— 派生索引(架构 §9)。随时可由 MySQL 全量重建,非权威存储。
    qdrant_url: str = "http://localhost:6333"
    qdrant_api_key: SecretStr = SecretStr("")
    # 向量维度。架构 §9.2 写"由 Embedding 网关决定(如 1024)";
    # 真实值待 P0-2 的 Embedding 选型。改它需要重建集合并全量重放(索引可重建,安全)。
    index_vector_size: int = 1024

    # RabbitMQ —— 异步任务(架构 §6.2;P0-3① 已裁决一期采用 RabbitMQ)。
    rabbitmq_url: str = "amqp://tileai:tileai@localhost:5672/"

    #: 与 broker 协商的心跳(秒);`None` 表示沿用 broker 的建议值。
    #:
    #: ⚠️ **必须大于"单次模型调用的最长阻塞时间"。** 消费端是**同步阻塞**的:
    #: 它等模型返回的那几分钟里**没机会给 broker 发心跳**,broker 会把它当死连接掐掉
    #: ⇒ 消息被重投 ⇒ **同一单跑两遍、白花一次模型调用**(2026-10-03 实测,§63)。
    #: 默认 900 覆盖 `gateway_image_timeout_seconds`(600)并留出余量。
    #:
    #: ⚠️ 心跳是**协商值**,取 min(客户端, 服务端)—— 所以 broker 侧也要放宽,
    #: 见 `deploy/docker-compose.dev.yml` 的 `-rabbit heartbeat 900`。**只改一边等于没改。**
    rabbitmq_heartbeat_seconds: int = 900

    # ------------------------------------------------------------------
    # 生成编排(T-1.11;架构 §6.2、§11.2)
    # ------------------------------------------------------------------
    # 生成消费端是否随本进程启动。本地联调要开;测试里关掉,免得后台线程
    # 抢消息导致断言不确定。
    generate_consumer_in_process: bool = True

    #: 多角度出图时,**把第一张的结果当作后面几张的参考图**(基准图)。
    #:
    #: 为什么需要:多角度是**四次独立调用**,模型之间互相看不见 —— 四张图
    #: "是同一个房间"原先只靠提示词请求,没有任何机制保证(实测出现过
    #: "每张像不同的房间",§61)。发一张基准图进去,模型才有图可依。
    #: 这也填上了架构 §15.3 里那个一直空着的「底图」位置。
    #:
    #: ⚠️ **2026-10-07:多角度已取消**(`angles` 上限锁为 1,§74)⇒
    #: **本开关当前不再生效** —— 单张路径下它一直是空操作(见 `build_prompts`
    #: 的 `anchor_first_view` 说明:"对单张是空操作")。
    #: **不删**:将来用多机位底图恢复多角度时还要用它。
    #:
    #: ⚠️ **仍未验证**,且**没有被否定**:§64.9 那次真机反馈是"整体乱画",
    #: **没有回答**"四张是不是同一个房间"这个判据(§64.10)。
    #: 恢复多角度时,它依然是**未经真实验证**的候选,不要当成已验证的能力。
    image_anchor_first_view: bool = True

    # 替身图的 URL 前缀(§29 阶段性收缩:本期图像用固定占位图)。
    # 留空则取 `java_base_url` —— 图必须由 **Java** 提供:前端只连 Java(不变量 3),
    # 挂在 Python 上本地能跑通,但上线时会被网关拦掉。
    placeholder_image_base_url: str = ""


settings = Settings()
