# -*- coding: utf-8 -*-
"""按行号打印原文 + 该行 YAML 实际解析出的键,作为修复前的证据"""
import sys, io, os, yaml

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))

TARGETS = {
    'openapi-common.yaml': [103, 124, 239, 241, 276, 300, 455, 456, 538, 539, 540],
    'openapi-admin.yaml': [819, 895, 896, 897],
    'openapi-internal.yaml': [63, 87, 288],
}

def all_mappings(tree, acc):
    if isinstance(tree, yaml.MappingNode):
        acc.append(tree)
        for k, v in tree.value:
            all_mappings(k, acc); all_mappings(v, acc)
    elif isinstance(tree, yaml.SequenceNode):
        for v in tree.value:
            all_mappings(v, acc)

for fn, linenos in TARGETS.items():
    path = os.path.join(ROOT, fn)
    with open(path, 'r', encoding='utf-8') as f:
        lines = f.read().split('\n')
    tree = yaml.compose('\n'.join(lines))
    maps = []
    all_mappings(tree, maps)
    print('=' * 78)
    print(fn)
    print('=' * 78)
    for ln in linenos:
        raw = lines[ln - 1]
        print(f'\nL{ln}  原文:')
        print(f'    {raw.strip()}')
        # 找出起始行 == ln 的 mapping 节点,列出它实际解析出的键
        for node in maps:
            st = node.start_mark.line + 1
            if st == ln:
                keys = [k.value for k, _ in node.value]
                junk = [k for k in keys
                        if not all(ch.isascii() for ch in k) or ' ' in k]
                print(f'    实际解析出的键: {keys}')
                if junk:
                    print(f'    >>> 撕裂产物(非法键): {junk}')
                break
    print()
