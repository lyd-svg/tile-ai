# -*- coding: utf-8 -*-
"""汇总 redocly / spectral 的 JSON 报告:按 severity=error 逐条列出"""
import sys, io, json, os, glob

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
R = os.path.join(ROOT, 'outputs', 'reports')

def load(p):
    try:
        with open(p, 'r', encoding='utf-8-sig') as f:
            return json.load(f)
    except Exception as e:
        return None

print('#' * 78)
print('# REDOCLY  —— 仅列 error')
print('#' * 78)
for p in sorted(glob.glob(os.path.join(R, 'redocly-*.json'))):
    d = load(p)
    name = os.path.basename(p).replace('redocly-', '').replace('.json', '')
    if not d:
        print(f'{name}: 报告无法解析'); continue
    probs = d.get('problems', [])
    errs = [x for x in probs if x.get('severity') == 'error']
    print(f'\n--- {name}  (error={len(errs)}, 全部={len(probs)}, totals={d.get("totals")}) ---')
    for e in errs:
        loc = (e.get('location') or [{}])[0]
        raw_src = loc.get('source', '?')
        if isinstance(raw_src, dict):
            raw_src = raw_src.get('$ref', str(raw_src))
        src = os.path.basename(str(raw_src))
        st = loc.get('start', {}) or {}
        print(f"  {src}:{st.get('line')}:{st.get('col')}  [{e.get('ruleId')}]  {e.get('message')}")

print()
print('#' * 78)
print('# SPECTRAL  —— 仅列 severity=0(error)')
print('#' * 78)
for p in sorted(glob.glob(os.path.join(R, 'spectral-*.json'))):
    d = load(p)
    name = os.path.basename(p).replace('spectral-', '').replace('.json', '')
    print(f'\n--- {name} ---')
    if not d:
        print('  报告无法解析/为空'); continue
    errs = [x for x in d if x.get('severity') == 0]
    print(f'  error={len(errs)}, 全部={len(d)}')
    for e in errs:
        rng = e.get('range', {}).get('start', {})
        print(f"  L{rng.get('line', 0)+1}:{rng.get('character', 0)+1}  [{e.get('code')}]  {e.get('message')}")
        print(f"        path: {e.get('path')}")
