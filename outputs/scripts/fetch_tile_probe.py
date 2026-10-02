"""抓一个瓷砖产品详情页,把原文与线索落盘 —— 用来搞清楚真实页面结构。

**这是一次性探针,不属于 7 个门禁,不参与成败判定。**

为什么要"先抓一页":写爬虫最容易犯的错是照着想象的结构写解析。
先把一个真实页面抓下来看清 `<img>` 的地址形态、字段怎么摆,再写解析器,
返工成本最低。

用法(零依赖,只用标准库):

    python outputs/scripts\\fetch_tile_probe.py "https://www.monalisa.com.cn/productcenter/info_itemid_13568.html"

产出:
    outputs/reports/tile-probe.html   原始 HTML(用编辑器打开看真实结构)
    outputs/reports/tile-probe.txt    抽取到的线索(标题 / 图片地址 / 可能的字段)

⚠️ 礼貌抓取:本脚本**只请求一次**。批量抓取时请自行加间隔(建议 >= 2 秒),
并先看该站的 robots.txt;这些图片与品牌信息有版权,仅可用于本机演示、不可公开传播。
"""

import re
import sys
import urllib.request
from pathlib import Path

REPORTS = Path(__file__).resolve().parents[1] / "reports"
TIMEOUT_SECONDS = 20

# 用真实浏览器 UA:不少站点对空 UA / 脚本 UA 直接返回 403 或验证页。
HEADERS = {
    "User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                   "AppleWebKit/537.36 (KHTML, like Gecko) "
                   "Chrome/124.0 Safari/537.36"),
    # 关掉压缩:urllib 不会自动解 gzip,拿到手就是一坨乱码
    "Accept-Encoding": "identity",
    "Accept-Language": "zh-CN,zh;q=0.9",
}


def fetch(url: str) -> tuple[bytes, str]:
    """取回页面字节与原编码名。"""
    request = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
        return response.read(), response.headers.get_content_charset() or ""


def decode(raw: bytes, charset: str) -> str:
    """解码。优先用响应头声明的编码,再退回页面 <meta> 里的,最后按 utf-8 猜。"""
    candidates = [charset]
    meta = re.search(rb'charset=["\']?([\w-]+)', raw[:4096], re.IGNORECASE)
    if meta:
        candidates.append(meta.group(1).decode("ascii", "ignore"))
    candidates.append("utf-8")
    # 国内老站点大量是 gbk/gb2312,放在最后兜底
    candidates.append("gb18030")

    for name in candidates:
        if not name:
            continue
        try:
            return raw.decode(name)
        except (LookupError, UnicodeDecodeError):
            continue
    return raw.decode("utf-8", "replace")


def image_urls(html: str, page_url: str) -> list[str]:
    """抽出所有图片地址 —— 包括 src、data-src(懒加载)、以及 CSS 里的 background。"""
    found: list[str] = []
    patterns = [
        r'<img[^>]+src=["\']([^"\']+)["\']',
        r'<img[^>]+data-src=["\']([^"\']+)["\']',       # 懒加载的常见写法
        r'<img[^>]+data-original=["\']([^"\']+)["\']',
        r'url\(["\']?([^)"\']+\.(?:jpg|jpeg|png|webp))["\']?\)',
    ]
    for pattern in patterns:
        for match in re.finditer(pattern, html, re.IGNORECASE):
            url = match.group(1).strip()
            if not url or url.startswith("data:"):
                continue
            found.append(url)

    # 去重保序
    unique: list[str] = []
    seen = set()
    for url in found:
        if url not in seen:
            seen.add(url)
            unique.append(url)
    return unique


def labelled_fields(html: str) -> list[tuple[str, str]]:
    """把"标签 + 值"形式的字段尽量捞出来(型号 / 规格 / 釉面工艺 …)。

    纯正则只能做到"尽力而为":页面结构千奇百怪,这一步的产出是**给人看的线索**,
    不是可以直接入库的数据。
    """
    labels = ["型号", "规格", "产品类别", "类别", "釉面工艺", "表面", "纹理",
              "材质", "颜色", "色系", "使用场景", "产品特性", "特性"]
    results: list[tuple[str, str]] = []
    for label in labels:
        # 标签后面跟一段 HTML,再跟一段纯文本
        pattern = re.compile(
            rf"{label}\s*[:：]?\s*(?:</?[^>]+>\s*){{0,6}}([^<>]{{1,60}})",
            re.IGNORECASE)
        for match in pattern.finditer(html):
            value = match.group(1).strip()
            if value and not value.startswith("{"):
                results.append((label, value))
                break
    return results


def main() -> int:
    if len(sys.argv) < 2:
        print("用法: python fetch_tile_probe.py <产品详情页 URL>")
        return 2

    url = sys.argv[1]
    REPORTS.mkdir(parents=True, exist_ok=True)

    print(f"抓取:{url}")
    try:
        raw, charset = fetch(url)
    except Exception as error:  # noqa: BLE001 —— 探针脚本,把原因原样打出来最有用
        print(f"抓取失败:{type(error).__name__}: {error}")
        print("若报 403/404,多半是 UA 或路径问题;把原始报错发回来即可。")
        return 1

    html = decode(raw, charset)
    (REPORTS / "tile-probe.html").write_text(html, encoding="utf-8")

    title = re.search(r"<title[^>]*>(.*?)</title>", html, re.IGNORECASE | re.DOTALL)
    images = image_urls(html, url)
    fields = labelled_fields(html)

    lines = [
        f"URL          : {url}",
        f"HTTP 字节数  : {len(raw)}",
        f"响应头编码   : {charset or '(未声明)'}",
        f"页面标题     : {title.group(1).strip() if title else '(无)'}",
        "",
        f"--- 图片地址({len(images)} 个)---",
    ]
    lines += [f"  {u}" for u in images] or ["  (没抽到,可能图片由 JS 动态插入)"]
    lines += ["", f"--- 疑似字段({len(fields)} 条)---"]
    lines += [f"  {k} = {v}" for k, v in fields] or ["  (没抽到)"]
    lines += [
        "",
        f"原始 HTML 已存:{REPORTS / 'tile-probe.html'}",
        "拿它确认「大图长什么样、是不是懒加载」之后,再写解析与导入 CSV。",
    ]

    text = "\n".join(lines)
    (REPORTS / "tile-probe.txt").write_text(text, encoding="utf-8")
    print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
