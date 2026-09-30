"""`prompt.py` 那两个取数接缝的 HTTP 实现(T-1.11)。

`app/agent/prompt.py` 的模块头第 4 条明说它**不取数**:SKU 详情与陈设配置分别经
`SkuSource` / `FurnishSource` 注入,那两个 `Protocol` 就声明在那边。本文件是它们的
实现 —— 把"取数"这件事留在编排层,`prompt.py` 保持无 I/O(测试不需要任何外部服务)。

## 按任务缓存

两个 Source 的实例都是**一个生成任务一个**(消费端在每条消息里新建):

- `HttpSkuSource` 缓存 `sku → 详情`。一个部位列表里同一个 SKU 可能被两个部位引用
  (同一块砖铺地面又上墙,PRD §5.2.4 允许),不缓存就是重复请求同一个接口。
- **取不到的也要缓存**(存 `None`)。否则一块已下架的砖会被每个引用它的部位各问一次,
  而答案每次都一样是"没有"。

为什么不做成跨任务共享的缓存:那会引入失效问题 —— 产品详情随时可能被后台改,
而"改到哪一刻为止"没有任何依据可循。一个任务的生命周期是几分钟,
在这个窗口内答案稳定;超出这个窗口就该重新问 Java。

## 失败一律降级,不抛

详见 `app/clients/java_api.py` 的两条不变量。这里补一条与提示词组装的接口:
`SkuSource.get` 取不到就返回 `None`,而 `prompt.py` 的 `_map_parts` 对 `None`
有明确定义的行为(保留部位、短语为空、记 WARN)。所以"Java 挂了"在这条链路上的
表现是**图照出、砖的描述缺失**,而不是生成失败 —— 这是刻意的(§13.3)。
"""

from __future__ import annotations

import logging

from app.agent.prompt import FurnishConfig
from app.clients.java_api import JavaApiClient

logger = logging.getLogger(__name__)


class HttpSkuSource:
    """`prompt.SkuSource` 的 HTTP 实现。**每个生成任务新建一个实例。**"""

    def __init__(self, client: JavaApiClient, *, trace_id: str = "") -> None:
        self._client = client
        self._trace_id = trace_id
        self._cache: dict[str, dict | None] = {}

    def get(self, sku: str) -> dict | None:
        if sku not in self._cache:
            self._cache[sku] = self._client.get_product(sku, trace_id=self._trace_id)
        return self._cache[sku]


class HttpFurnishSource:
    """`prompt.FurnishSource` 的 HTTP 实现。**每个生成任务新建一个实例。**

    `get(space, style)` 在 `prepare_prompts` 里只会被调一次,所以这个缓存目前
    不起作用 —— 留着是因为那个签名允许重复调用,而"同一个 (空间, 风格) 问两遍"
    的代价是一次多余的网络往返。缓存键必须是 `(space, style)` 而不是 `space`:
    一个空间可配多种风格,按空间做键会把 A 风格的陈设用到 B 风格上。
    """

    def __init__(self, client: JavaApiClient, *, trace_id: str = "") -> None:
        self._client = client
        self._trace_id = trace_id
        self._cache: dict[tuple[str, str], FurnishConfig] = {}

    def get(self, space: str, style: str) -> FurnishConfig:
        key = (space, style)
        cached = self._cache.get(key)
        if cached is not None:
            return cached
        config = self._fetch(space, style)
        self._cache[key] = config
        return config

    def _fetch(self, space: str, style: str) -> FurnishConfig:
        data = self._client.get_furnish_prompts(space, style, trace_id=self._trace_id)
        if data is None:
            # 与"后台没配"走同一个降级值(都是不放陈设段),但日志里要分得清:
            # 一个是**部署/网络**问题,一个是**运营**问题,该找的人不一样。
            logger.warning("取陈设配置失败(space=%s, style=%s):本次生成不含陈设段", space, style)
            return FurnishConfig.missing()

        prompt = str(data.get("prompt") or "").strip()
        configured = bool(data.get("furnishConfigured")) and bool(prompt)
        if not configured:
            logger.warning("陈设未配置(space=%s, style=%s):本次生成不含陈设段", space, style)
        return FurnishConfig(
            prompt=prompt,
            cover_image=data.get("coverImage") or None,
            configured=configured,
        )
