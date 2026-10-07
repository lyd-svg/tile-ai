"""测试级共用夹具。

**这里只做一件事:把内部接口的服务令牌闸门在测试里显式关掉。**

为什么需要它:`require_service_token` 的设计是"**未配置令牌就放行**"(便于本地联调,
见架构 §13.6)。但那是**看开发机的 `.env` 说话**的 —— 一旦按架构把令牌真配上,
所有没带令牌头的端点测试会**集体 401**。

2026-10-03 实测踩到:`SERVICE_TOKEN` 一写进 `.env`,`test_layout_parse` /
`test_complete_parts` / `test_chat_stream` **30 条同时红**,而且症状是 `KeyError`
(响应体里没有我们的信封,只有 FastAPI 的 `{"detail": ...}`)——
看起来像"端点坏了",实际只是"测试默默依赖开发机没配令牌"。

测试不该依赖开发机的 `.env` 长什么样。所以在这里**显式**置空;
闸门本身由各文件里的 `test_endpoint_enforces_service_token_when_configured`
单独覆盖(它们自己 monkeypatch `internal_api.settings`,不受这里影响)。
"""

import pytest
from pydantic import SecretStr

from app.api import internal as internal_api
from app.core.config import settings


class _NoServiceToken:
    """闸门替身:一个"没有配令牌"的 settings。

    只覆盖闸门读的那**一个**字段 —— `app/api/internal.py` 里 `settings`
    也只用于这一处(见该文件第 90 行),所以换掉整个对象是安全的。
    """

    service_token = SecretStr("")


@pytest.fixture(autouse=True)
def open_service_token_gate(monkeypatch):
    """默认放行内部接口 —— 让端点测试只关心自己的靶子,不关心本机配没配令牌。"""
    monkeypatch.setattr(internal_api, "settings", _NoServiceToken)


@pytest.fixture(autouse=True)
def consumers_never_start_in_tests(monkeypatch):
    """**测试里永不启动消费端**(2026-10-07,§79)。

    两个消费端都由 `app/main.py` 的 `lifespan` 启动,而 `lifespan` **只在
    `with TestClient(app)` 这种用法下才会跑**。目前仓库里三处 `TestClient(app)`
    都是模块级赋值(`test_chat_stream` / `test_complete_parts` / `test_layout_parse`),
    **没走 `with`,所以今天并不会真的启动** —— 这道夹具是**防未来的**。

    为什么值得防:一旦谁把那三处改成 `with TestClient(app)`,后果是**静默的**:
      - 自动拉起**索引同步**消费端 ⇒ 连的是**开发机 `.env` 里的那个 RabbitMQ**
        (默认 vhost `/`,即用户正式队列),并往**同一套 Qdrant** 里写;
      - 自动拉起**生成**消费端 ⇒ 更贵,它真的会**调图像模型 API 花钱**;
      - 而且两个线程都是 daemon、只打印日志,测试全绿也看不出来。

    这与本文件开头那道闸门是**同一类问题**:测试不该依赖(更不该副作用于)
    开发机的配置。**置 False 而不是删掉消费端** —— `app/main.py` 的启动路径
    本身仍由 §79 的真机端到端覆盖。
    """
    monkeypatch.setattr(settings, "index_sync_consumer_in_process", False)
    monkeypatch.setattr(settings, "generate_consumer_in_process", False)
