"""抓蒙娜丽莎官网产品,输出成**后台批量导入能直接吃的 CSV**。

**这是一次性取数工具,不属于 7 个门禁,不参与成败判定。**

列定义与后端 `ImportColumn` 对齐(架构 §8.7):

    产品编号,产品名称,规格,材质,光泽度,花色纹理,单价(元/㎡),适用空间,风格标签,封面图URL,多角度图URL

用法:

    python outputs/scripts/scrape_tile_products.py --limit 25
    python outputs/scripts/scrape_tile_products.py --limit 100 --delay 2.0

⚠️ 四件必须知道的事:

1. **单价是编的,但按规格与材质推导、且每个产品各不相同。** 官网不公开价格,而导入
   模板把 `单价(元/㎡)` 列为必填。脚本按"规格定区间 × 材质定系数",再用产品编号做
   种子在区间内取值 —— 所以**可复现、彼此不同、量级合理**,但它**仍然是编的**,
   不是真实成交价。演示报价的数字不要当真实价格引用。
2. **风格标签是推断的,会猜错。** 官网不提供风格,脚本按名称/纹理里的关键词映射到
   字典代码(`modern` / `nordic` / `chinese` / `cream`)—— 比如"奥斯银灰"→modern、
   "凝砂花涧"→nordic。**这是有根据的猜,不是事实**;演示前可以人工过一遍。
3. **空间做了映射,匹配不上的丢弃。** 你库里的空间字典只有
   living_room / bedroom / bathroom / kitchen / balcony 五项;官网的"餐厅、玄关、
   过道、书房、儿童房"没有对应项,不会硬塞。
   ⚠️ 空间与风格两列**都必须是字典里的 key**(英文),中文会被导入拒掉 ——
   但前端显示的是字典的中文 label,用户看不到英文。
4. **图片是热链官网地址,不是下载到本机。** 因为出图时"垫图"是由阿里云服务器去取图,
   本机 localhost 的图它够不着。下载到本机的版本另存一份,仅用于你看。

⚠️ 版权:抓来的图片与品牌信息归蒙娜丽莎所有,仅限本机演示、答辩注明来源,
**不得公开传播或商用**。请控制抓取频率(默认 1.5 秒/请求)并先看该站 robots.txt。
"""

import argparse
import csv
import hashlib
import html as html_module
import re
import time
import urllib.request
from pathlib import Path

BASE = "https://www.monalisa.com.cn"
LIST_URL = BASE + "/productcenter/list_lcid_0_page_{page}.html"
REPORTS = Path(__file__).resolve().parents[1] / "reports"
IMAGES_DIR = REPORTS / "monalisa-images"

HEADERS = {
    "User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                   "AppleWebKit/537.36 (KHTML, like Gecko) "
                   "Chrome/124.0 Safari/537.36"),
    "Accept-Encoding": "identity",       # urllib 不会解 gzip
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Referer": BASE + "/",
}

# 与后端 ImportColumn 的表头逐字一致 —— 差一个字导入就认不出那一列(它按表头反查)
CSV_HEADERS = ["产品编号", "产品名称", "规格", "材质", "光泽度", "花色纹理",
               "单价(元/㎡)", "适用空间", "风格标签", "封面图URL", "多角度图URL"]

# 官网"使用场景" → 库里的空间字典 key。
# 字典只有五项(见 t_dict),官网多出来的词**刻意不映射** —— 硬塞一个不存在的
# code,前端筛选与出图提示词都会认不出来。
SPACE_MAP = {
    "客厅": "living_room",
    "卧室": "bedroom",
    "卫生间": "bathroom",
    "卫浴间": "bathroom",
    "厨房": "kitchen",
    "阳台": "balcony",
    "露台": "balcony",
}

# ---------------------------------------------------------------------------
# 单价:官网不公开价格,而模板必填。按「规格定区间 × 材质定系数」推导。
#
# ⚠️ 这是**编的**。量级取自常见市场区间,不是任何真实成交价。
# 之所以不全填同一个数:同一个价目表里所有砖一个价,一看就是假数据,
# 而且演示"按砖算价"时不同砖算出同一个金额,对比不出来。
# ---------------------------------------------------------------------------

# 规格关键词 → (元/㎡ 下限, 上限)。从大到小匹配,先命中者生效。
PRICE_RANGE_BY_SPEC = [
    ("1600×3200", (420, 780)),
    ("1200×2700", (400, 720)),
    ("1200×2400", (380, 680)),
    ("900×2700", (360, 640)),
    ("900×1800", (230, 390)),
    ("750×1500", (150, 270)),
    ("600×1200", (105, 190)),
    ("800×800", (75, 145)),
    ("600×600", (62, 118)),
    ("400×800", (48, 88)),
    ("300×600", (40, 72)),
    ("200×1200", (95, 165)),
]

# 材质 → 系数。大板/岩板工艺更贵,瓷片内墙砖更便宜。
PRICE_FACTOR_BY_MATERIAL = [
    ("岩板", 1.45),
    ("大板", 1.18),
    ("薄板", 1.10),
    ("瓷片", 0.72),
    ("内墙砖", 0.72),
]

DEFAULT_PRICE_RANGE = (90, 170)


def price_of(spec: str, material: str, sku: str) -> str:
    """按规格与材质推一个**确定但各不相同**的单价。

    用 SKU 的哈希当种子 —— 同一个产品每次都得到同一个价(可复现),
    不同产品落在区间里的不同位置(看起来像真的价目表)。
    """
    low, high = DEFAULT_PRICE_RANGE
    for keyword, bounds in PRICE_RANGE_BY_SPEC:
        if keyword in (spec or ""):
            low, high = bounds
            break

    factor = 1.0
    for keyword, value in PRICE_FACTOR_BY_MATERIAL:
        if keyword in (material or ""):
            factor = value
            break

    low, high = low * factor, high * factor

    # 区间内按 10 元一档取,末尾落成 8 —— 国内挂牌价常见"…8"结尾,像真的
    steps = max(1, int((high - low) // 10))
    seed = int(hashlib.sha256(sku.encode("utf-8")).hexdigest()[:8], 16)
    value = low + (seed % (steps + 1)) * 10
    value = round(value / 10) * 10 - 2
    return f"{max(low, min(high, value)):.2f}"


# ---------------------------------------------------------------------------
# 风格标签:官网不提供,**按名称/纹理里的关键词推断**成字典代码。
# ⚠️ 这是有根据的猜,不是事实。顺序即优先级,先命中者生效。
# ---------------------------------------------------------------------------

STYLE_RULES = [
    (("木纹", "原木", "木"), ["nordic", "cream"]),
    (("花", "拼花", "几何"), ["nordic"]),
    (("中式", "禅", "水墨", "山水", "韵", "雅", "墨韵"), ["chinese"]),
    (("金", "银", "铁", "灰", "黑", "曜", "星", "砂", "岩"), ["modern"]),
    (("米", "黄", "奶", "暖", "白", "云", "雪", "玉", "浅"), ["cream", "modern"]),
]


def styles_of(name: str, pattern: str) -> str:
    """名称与纹理里找线索 → 风格字典代码。都匹配不上时按现代处理。"""
    haystack = f"{name}{pattern}"
    for keywords, codes in STYLE_RULES:
        if any(keyword in haystack for keyword in keywords):
            return ";".join(codes)
    return "modern"


def fetch(url: str) -> str:
    request = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(request, timeout=25) as response:
        raw = response.read()
    for encoding in ("utf-8", "gb18030"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", "replace")


def clean(text: str) -> str:
    """去标签、还原实体、压空白。"""
    text = re.sub(r"<[^>]+>", "", text)
    text = html_module.unescape(text)
    return re.sub(r"\s+", " ", text).strip()


def product_ids(page_html: str) -> list[str]:
    """列表页里的产品 id,去重保序。"""
    ids = re.findall(r"info_itemid_(\d+)\.html", page_html)
    return list(dict.fromkeys(ids))


def block_of(page_html: str, marker: str) -> str:
    """取某个容器内的片段 —— 用 `</ul>` 收尾就够了,页面结构很规整。"""
    start = page_html.find(marker)
    if start < 0:
        return ""
    end = page_html.find("</ul>", start)
    return page_html[start:end if end > start else len(page_html)]


def gallery_urls(page_html: str) -> list[str]:
    """产品图:ul.slider-for 里每个 li 的 <a href>。"""
    block = block_of(page_html, 'class="ul slider-for"')
    urls = re.findall(r'href="(/uploadfiles/[^"]+)"', block)
    return list(dict.fromkeys(urls))


def scene_urls(page_html: str) -> list[str]:
    """应用空间实景图:pro_slider_list。多出来的一张底图,另存供你看。"""
    block = block_of(page_html, "pro_slider_list")
    urls = re.findall(r'href="(/uploadfiles/[^"]+)"', block)
    return list(dict.fromkeys(urls))


def fields_of(page_html: str) -> dict[str, str]:
    """`<span class="name">标签：</span>值` —— 官网字段的统一写法。"""
    pairs = re.findall(r'<span class="name">\s*([^<]*?)\s*[:：]\s*</span>\s*([^<]*)', page_html)
    result: dict[str, str] = {}
    for label, value in pairs:
        label = label.strip()
        if label and label not in result:
            result[label] = clean(value)
    return result


def title_and_sku(page_html: str) -> tuple[str, str]:
    """h2 里是「产品名称 型号」,型号用来当产品编号(SKU)。"""
    match = re.search(r'<h2 class="txt_h2">\s*(.*?)\s*</h2>', page_html, re.DOTALL)
    if not match:
        return "", ""
    text = clean(match.group(1))
    # 型号里带连字符/字母数字,名称是中文 —— 从右往左切出尾部那段型号
    parts = text.rsplit(" ", 1)
    if len(parts) == 2 and re.search(r"[A-Za-z]", parts[1]):
        return parts[0].strip(), parts[1].strip()
    return text, text


def map_spaces(raw: str) -> str:
    codes: list[str] = []
    for word in re.split(r"[、,，/\s]+", raw or ""):
        code = SPACE_MAP.get(word.strip())
        if code and code not in codes:
            codes.append(code)
    return ";".join(codes)


def download_image(relative_url: str, target_dir: Path, name: str) -> bool:
    """把图另存一份到本机 —— 只为方便你看,不进 CSV。"""
    try:
        request = urllib.request.Request(BASE + relative_url, headers=HEADERS)
        with urllib.request.urlopen(request, timeout=25) as response:
            data = response.read()
        (target_dir / name).write_bytes(data)
        return True
    except Exception:
        return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=25, help="抓多少个产品(默认 25)")
    parser.add_argument("--delay", type=float, default=1.5, help="每个请求之间的间隔秒数")
    parser.add_argument("--no-download", action="store_true", help="只写 CSV,不下载图片到本机")
    args = parser.parse_args()

    REPORTS.mkdir(parents=True, exist_ok=True)
    IMAGES_DIR.mkdir(parents=True, exist_ok=True)

    # ---- 1. 列表页拿产品 id ----
    ids: list[str] = []
    page = 1
    while len(ids) < args.limit:
        print(f"列表页 {page} …")
        page_ids = product_ids(fetch(LIST_URL.format(page=page)))
        if not page_ids:
            print("  没有更多产品了,停止翻页")
            break
        ids.extend(page_ids)
        page += 1
        time.sleep(args.delay)
    ids = list(dict.fromkeys(ids))[: args.limit]

    rows: list[list[str]] = []
    skipped: list[str] = []

    # ---- 2. 逐个详情页 ----
    for index, item_id in enumerate(ids, start=1):
        url = f"{BASE}/productcenter/info_itemid_{item_id}.html"
        try:
            page_html = fetch(url)
        except Exception as error:
            skipped.append(f"{item_id}: 抓取失败 {error}")
            continue

        name, sku = title_and_sku(page_html)
        fields = fields_of(page_html)
        spaces = map_spaces(fields.get("使用场景", ""))
        photos = gallery_urls(page_html)
        scenes = scene_urls(page_html)

        if not sku or not photos:
            skipped.append(f"{item_id}: 缺型号或缺产品图(name={name!r})")
            continue

        spec = fields.get("主要规格", "")
        material = fields.get("产品类别", "")
        pattern = fields.get("纹理", "")

        rows.append([
            sku,
            name,
            spec,
            material,
            fields.get("釉面工艺", ""),
            pattern,
            price_of(spec, material, sku),         # 编的,但按规格/材质推导且各不相同
            spaces,
            styles_of(name, pattern),              # 推断的,会猜错;代码而非中文
            BASE + photos[0],                      # 封面图
            ";".join(BASE + u for u in photos[1:]),  # 多角度图
        ])

        # 图上另存一份,方便肉眼看效果
        if not args.no_download:
            for n, rel in enumerate(photos, start=1):
                download_image(rel, IMAGES_DIR, f"{sku}_产品{n}.jpg")
            for n, rel in enumerate(scenes, start=1):
                download_image(rel, IMAGES_DIR, f"{sku}_空间{n}.jpg")

        print(f"  [{index}/{len(ids)}] {sku} {name}  ¥{rows[-1][6]}/㎡  "
              f"图{len(photos)}张 空间图{len(scenes)}张  "
              f"空间[{spaces or '—'}] 风格[{rows[-1][8]}]")
        time.sleep(args.delay)

    # ---- 3. 写 CSV(带 UTF-8 BOM,否则 Excel 打开中文乱码)----
    out = REPORTS / "monalisa-products.csv"
    with out.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(CSV_HEADERS)
        writer.writerows(rows)

    print()
    print(f"完成:成功 {len(rows)} 条 / 跳过 {len(skipped)} 条")
    for note in skipped:
        print(f"  跳过 {note}")
    print(f"CSV      : {out}")
    print(f"图片目录 : {IMAGES_DIR}")
    print()
    print("⚠️ 单价是按规格/材质推导的**编造值**(官网不公开价格),不是真实成交价。")
    print("⚠️ 风格标签是按名称/纹理**推断**的,会猜错,演示前建议人工过一遍。")
    print("⚠️ 图片版权归蒙娜丽莎,仅限本机演示。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
