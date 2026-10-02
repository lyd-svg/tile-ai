#!/usr/bin/env python
"""把本地嵌入模型下到 `tile-ai-agent/models/` 下(架构 §9.1 的索引侧准备)。

**为什么要这个工具**:模型权重有 1GB 出头,`huggingface.co` 在国内经常慢到没法下。
这里默认走 **`hf-mirror.com`**(国内可用的官方镜像),并把模型落到仓库内的固定目录,
好让 `MODEL_EMBEDDING_MODEL` 留空就能用(默认目录见 `gateway/factory.py`)。

用法:
    python tools/download_embedding.py                     # 下默认模型
    HF_ENDPOINT=https://huggingface.co python tools/...    # 走官方(能通时)
    python tools/download_embedding.py --model Qwen/Qwen3-Embedding-4B

⚠️ **换模型就是换向量空间**:下完必须重建索引(`python tools/rebuild_index.py`),
否则新的查询向量会和旧的索引进同一个集合,检索结果是乱的。
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

#: 默认与 `gateway/factory.py` 的 `_LOCAL_EMBEDDING_DEFAULT_MODEL` 保持一致。
_AGENT_DIR = Path(__file__).resolve().parent.parent / "tile-ai-agent"
_MODELS_DIR = _AGENT_DIR / "models"
DEFAULT_MODEL = "Qwen/Qwen3-Embedding-0.6B"
DEFAULT_ENDPOINT = "https://hf-mirror.com"

#: 不下这些:都是同权重换了个容器格式,白占一个 G。
_SKIP = ["*.h5", "*.ot", "*.msgpack", "onnx/*", "*.onnx"]


def main() -> int:
    parser = argparse.ArgumentParser(description="下本地嵌入模型到 tile-ai-agent/models/")
    parser.add_argument("--model", default=DEFAULT_MODEL, help=f"默认 {DEFAULT_MODEL}")
    parser.add_argument("--endpoint", default=os.getenv("HF_ENDPOINT", DEFAULT_ENDPOINT),
                        help=f"下载源,默认 {DEFAULT_ENDPOINT}(国内镜像)")
    args = parser.parse_args()

    # huggingface_hub 读的是环境变量,所以在这里设而不是当参数传
    os.environ.setdefault("HF_ENDPOINT", args.endpoint)
    # ⚠️ **必须关掉 Xet 传输**:huggingface_hub 新版默认用它,而 Xet 打的是另一个域名
    # (`cas-server.xethub.hf.co`),镜像站(如 hf-mirror)不代理那个域名 ——
    # 症状是下载中途报 `401 Unauthorized ... xethub.hf.co`,看起来像"没登录",
    # 其实是传输协议不通。关掉它就走经典的 HTTPS 下载,镜像才能生效。
    os.environ.setdefault("HF_HUB_DISABLE_XET", "1")
    try:
        from huggingface_hub import snapshot_download
    except ImportError:
        print("缺 huggingface_hub —— 它随 sentence-transformers 一起装。"
              "先 `pip install sentence-transformers`", file=sys.stderr)
        return 1

    target = _MODELS_DIR / args.model.split("/")[-1]
    target.mkdir(parents=True, exist_ok=True)
    print(f"下载 {args.model}")
    print(f"  源:{args.endpoint}")
    print(f"  到:{target}")

    snapshot_download(
        repo_id=args.model,
        local_dir=str(target),
        ignore_patterns=_SKIP,
    )

    print("完成。接下来:")
    print("  1. 确认 tile-ai-agent/.env 里 MODEL_EMBEDDING_PROVIDER=local")
    print("  2. **重建索引**(换模型必须重建):python tools/rebuild_index.py")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
