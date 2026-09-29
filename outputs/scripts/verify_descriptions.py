# -*- coding: utf-8 -*-
"""修复后:确认 16 处字段的 description 已完整(不再被截断),且无伪键残留"""
import sys, io, os, yaml

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))

# (文件, JSON 路径, 期望的完整描述)
CASES = [
    ('openapi-common.yaml', ['components','schemas','PageData','properties','page'], '当前页,从 1 开始'),
    ('openapi-common.yaml', ['components','schemas','Sku','properties','sku'], '产品编号(sku_code,唯一)'),
    ('openapi-common.yaml', ['components','schemas','Sku','properties','spec'], '规格,如 750×1500mm'),
    ('openapi-common.yaml', ['components','schemas','PartSelection','properties','area'], '前端可传可不传'),
    ('openapi-common.yaml', ['components','schemas','QuoteItem','properties','lossRate'], '损耗率,如 0.06'),
    ('openapi-common.yaml', ['components','schemas','UnitTemplate','properties','areaRange'], '面积区间,例:80-100㎡'),
    ('openapi-common.yaml', ['components','schemas','UnitTemplate','properties','structure'], '户型结构,例:三室两厅'),
    ('openapi-common.yaml', ['components','schemas','Dict','properties','key'], '存储键(对外提交值),如 living_room'),
    ('openapi-common.yaml', ['components','schemas','Dict','properties','label'], '展示名,如 客厅'),
    ('openapi-common.yaml', ['components','schemas','Dict','properties','sort'], '排序,升序'),
    ('openapi-admin.yaml',  ['components','schemas','ProductUpsert','properties','sku'], '产品编号(唯一,业务主键)'),
    ('openapi-admin.yaml',  ['components','schemas','DictUpsert','properties','key'], '存储键(对外提交值),如 living_room'),
    ('openapi-admin.yaml',  ['components','schemas','DictUpsert','properties','label'], '展示名,如 客厅'),
    ('openapi-admin.yaml',  ['components','schemas','DictUpsert','properties','sort'], '排序,升序'),
    ('openapi-internal.yaml', ['paths','/internal/agent/chat','post','requestBody','content','application/json','schema','properties','userId'], '用于鉴权与留痕'),
    ('openapi-internal.yaml', ['paths','/internal/generate/callback','post','requestBody','content','application/json','schema','properties','taskId'], '回调幂等键'),
]

ALL_FILES = ['openapi-common.yaml','openapi-c.yaml','openapi-admin.yaml','openapi-internal.yaml']
docs = {}
for f in ALL_FILES:
    with open(os.path.join(ROOT, f), 'r', encoding='utf-8') as fh:
        docs[f] = yaml.safe_load(fh)

ok = bad = 0
for fn, path, expect in CASES:
    node = docs[fn]
    for p in path:
        node = node[p]
    desc = node.get('description', '')
    keys = list(node.keys())
    good = expect in desc
    # 伪键:key 含空格或非 ASCII
    junk = [k for k in keys if ' ' in k or not all(ch.isascii() for ch in k)]
    if good and not junk:
        ok += 1
        print(f"  ✓ {fn} :: {'.'.join(path[-3:])}")
        print(f"      description = {desc!r}")
    else:
        bad += 1
        print(f"  ✗ {fn} :: {'.'.join(path[-3:])}")
        print(f"      description = {desc!r}  (期望含 {expect!r})")
        print(f"      伪键 = {junk}")

print()
print(f'描述完整且无伪键: {ok} / {len(CASES)}')
print('RESULT:', 'ALL OK' if bad == 0 else f'{bad} 处异常')

# 额外:全局断言 —— 每个 schema 属性的键必须全是 ASCII 关键字
import re
ALLOWED_NON_ASCII = set()
global_bad = 0
print()
print('全局扫描:任何 mapping 中出现非 ASCII 键即报告')
for fn in ['openapi-common.yaml','openapi-c.yaml','openapi-admin.yaml','openapi-internal.yaml']:
    hits = []
    def walk(n, p):
        if isinstance(n, dict):
            for k, v in n.items():
                if isinstance(k, str) and not k.isascii():
                    hits.append(f'{p}/{k}')
                walk(v, f'{p}/{k}')
        elif isinstance(n, list):
            for i, v in enumerate(n):
                walk(v, f'{p}[{i}]')
    walk(docs[fn], '')
    print(f'  {fn}: {len(hits)} 处非 ASCII 键 {hits if hits else ""}')
    if hits:
        global_bad += len(hits)

print()
if bad or global_bad:
    print(f'CI 判据:描述截断 {bad} 处 / 全局伪键 {global_bad} 处 -> 退出码 1')
    sys.exit(1)
print('CI 判据:全部通过 -> 退出码 0')
sys.exit(0)
