# -*- coding: utf-8 -*-
"""按文件统计 redocly 命中的规则分布,用于决定 redocly.yaml 关哪些规则"""
import sys, io, json, glob, os
from collections import Counter

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
R = os.path.join(ROOT, 'outputs', 'reports')

for p in sorted(glob.glob(os.path.join(R, 'redocly-*.json'))):
    name = os.path.basename(p).replace('redocly-', '').replace('.json', '')
    try:
        with open(p, 'r', encoding='utf-8-sig') as f:
            d = json.load(f)
    except Exception as e:
        print(f'{name}: 无法解析 {e}')
        continue
    c = Counter()
    for x in d.get('problems', []):
        c[(x.get('severity'), x.get('ruleId'))] += 1
    print(f'--- {name} ---')
    for (sev, rule), n in sorted(c.items(), key=lambda kv: (-kv[1])):
        print(f'   {sev:<8} {rule:<26} {n}')
    print()
