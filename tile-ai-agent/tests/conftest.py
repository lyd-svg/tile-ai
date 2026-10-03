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
