# -*- coding: utf-8 -*-
"""
扫描 YAML 流式映射(flow mapping)中所有「逗号撕裂」——含非 ASCII 型与被静默吞掉的 ASCII 型。
做法:把每行的 {...} 按顶层逗号切开,每个片段必须是 `合法key: value`。
片段 key 含空格/CJK、或整段没有冒号 => 撕裂产物。
"""
import sys, io, re, os

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
FILES = ['openapi-common.yaml', 'openapi-c.yaml', 'openapi-admin.yaml', 'openapi-internal.yaml']

KEY_RE = re.compile(r'^[A-Za-z_$][A-Za-z0-9_$.\-]*$')

def find_flow_mappings(line):
    """返回该行所有顶层 {...} 的 (起始列, 内文)"""
    out = []
    depth = 0
    start = None
    for i, ch in enumerate(line):
        if ch == '{':
            if depth == 0:
                start = i
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0 and start is not None:
                out.append((start, line[start + 1:i]))
                start = None
    return out

def split_top_commas(s):
    """按顶层逗号切分,跳过引号内与嵌套括号内"""
    parts, buf = [], []
    depth = 0
    quote = None
    for ch in s:
        if quote:
            buf.append(ch)
            if ch == quote:
                quote = None
            continue
        if ch in '"\'':
            quote = ch
            buf.append(ch)
        elif ch in '[{(':
            depth += 1
            buf.append(ch)
        elif ch in ']})':
            depth -= 1
            buf.append(ch)
        elif ch == ',' and depth == 0:
            parts.append(''.join(buf))
            buf = []
        else:
            buf.append(ch)
    if buf:
        parts.append(''.join(buf))
    return parts

def key_of(frag):
    """取片段顶层冒号前的 key;无冒号返回 None"""
    depth = 0
    quote = None
    for i, ch in enumerate(frag):
        if quote:
            if ch == quote:
                quote = None
            continue
        if ch in '"\'':
            quote = ch
        elif ch in '[{(':
            depth += 1
        elif ch in ']})':
            depth -= 1
        elif ch == ':' and depth == 0:
            return frag[:i].strip()
    return None

total = 0
for fn in FILES:
    path = os.path.join(ROOT, fn)
    with open(path, 'r', encoding='utf-8') as f:
        lines = f.read().split('\n')
    hits = []
    for ln, line in enumerate(lines, 1):
        for col, inner in find_flow_mappings(line):
            for frag in split_top_commas(inner):
                if not frag.strip():
                    continue
                k = key_of(frag)
                bad = False
                reason = ''
                if k is None:
                    bad, reason = True, '片段无 key(纯值,被逗号切断)'
                elif not KEY_RE.match(k):
                    bad, reason = True, f'key 非法: {k!r}'
                if bad:
                    hits.append((ln, col + 1, reason, line.strip()))
    if hits:
        print('=' * 78)
        print(f'{fn}   共 {len(hits)} 处')
        print('=' * 78)
        for ln, col, reason, raw in hits:
            print(f'  L{ln} (col {col})  {reason}')
            print(f'      {raw}')
        print()
    total += len(hits)
print(f'合计: {total} 处逗号撕裂')
