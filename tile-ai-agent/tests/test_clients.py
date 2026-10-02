"""T-1.6:共享客户端的惰性单例(`app/core/clients.py`)。

这个文件的由来是一处**只在真实服务里才会暴露的死锁**:`get_llm_gateway()` 在持锁
期间去调 `get_redis_client()`,而后者要拿同一把锁 —— 用普通 `threading.Lock` 时
**自己把自己锁死**,对话请求永久挂起、日志里一个字都没有。

单元测试原本抓不到它(测试里整段依赖都被换成了替身,`clients` 从没被真正走过),
所以补这一条:用"另起线程 + 超时"把死锁变成一次**失败**,而不是一次静默挂起。
"""

from __future__ import annotations

import threading

from app.core import clients
from app.gateway import factory


def test_get_llm_gateway_does_not_deadlock(monkeypatch):
    """先取 redis、再取网关 —— 顺序里藏着一把不可重入的锁(历史真事故)。"""
    clients.reset_clients()
    monkeypatch.setattr(clients, "_connect_redis", lambda: object())
    monkeypatch.setattr(factory, "build_llm_gateway", lambda **kwargs: "fake-gateway")

    result: dict[str, object] = {}

    def run() -> None:
        result["redis"] = clients.get_redis_client()
        result["llm"] = clients.get_llm_gateway()

    thread = threading.Thread(target=run, daemon=True)
    thread.start()
    thread.join(timeout=5)

    try:
        assert not thread.is_alive(), "get_llm_gateway 卡死了 —— 锁不可重入(见模块头)"
        assert result["llm"] == "fake-gateway"
    finally:
        clients.reset_clients()


def test_llm_gateway_is_cached(monkeypatch):
    """网关只装配一次 —— 熔断器/失败率统计要有跨请求的生命周期。"""
    clients.reset_clients()
    monkeypatch.setattr(clients, "_connect_redis", lambda: object())
    builds: list[int] = []
    monkeypatch.setattr(factory, "build_llm_gateway",
                        lambda **kwargs: builds.append(1) or "gateway")

    try:
        first = clients.get_llm_gateway()
        second = clients.get_llm_gateway()
        assert first is second
        assert len(builds) == 1, "重复装配会让熔断器永远是全新的,等于没有熔断"
    finally:
        clients.reset_clients()
