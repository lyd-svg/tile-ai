# -*- coding: utf-8 -*-
"""
检测 YAML 流式映射(flow mapping)中「未加引号的中文逗号」导致的键值撕裂。
原理:YAML flow 上下文里 `,` 是分隔符。写
    description: 规格,如 750x1500mm
会解析成 description='规格' 和 '如 750x1500mm'=None 两个键。
用 yaml.compose() 拿节点行号,精确定位。
"""
import sys, io, yaml

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

FILES = ['openapi-common.yaml', 'openapi-c.yaml', 'openapi-admin.yaml', 'openapi-internal.yaml']

# OpenAPI Schema Object 的合法关键字(用于判断哪些键是"撕裂"出来的异物)
SCHEMA_KEYWORDS = {
    'type', 'format', 'description', 'example', 'default', 'enum', 'required',
    'properties', 'items', 'allOf', 'oneOf', 'anyOf', 'not', 'nullable',
    'minimum', 'maximum', 'minItems', 'maxItems', 'minLength', 'maxLength',
    'pattern', 'additionalProperties', 'title', '$ref', 'readOnly', 'writeOnly',
    'deprecated', 'uniqueItems', 'multipleOf', 'externalDocs', 'xml', 'discriminator',
    'x-todo', 'x-source', 'x-direction', 'x-example', 'x-unit', 'x-note',
}

def walk(node, path, out, parent_is_properties=False):
    """递归遍历 compose 出来的节点树,收集异常键"""
    if isinstance(node, yaml.MappingNode):
        keys = []
        for k_node, v_node in node.value:
            keys.append(k_node)
        for k_node, v_node in node.value:
            key = k_node.value
            lineno = k_node.start_mark.line + 1
            cur = path + [key]
            # 判断:该 mapping 是否位于 properties / 顶层 schema 定义等位置
            # 异物键的特征:含空格或非 ASCII 且明显是句子片段
            if any(ch in key for ch in (' ', '，')) or (key and not key.isascii() and key not in ('§',)):
                out.append((lineno, key, '/'.join(map(str, cur)), node))
            walk(v_node, cur, out)
    elif isinstance(node, yaml.SequenceNode):
        for i, item in enumerate(node.value):
            walk(item, path + [i], out)

def scan(fname):
    with open(fname, 'r', encoding='utf-8') as f:
        text = f.read()
    tree = yaml.compose(text)
    out = []
    walk(tree, [], out)

    # 过滤掉"合法中文键"(本契约中基本没有;列出以便人工复核)
    findings = []
    for lineno, key, path, node in out:
        findings.append((lineno, key, path))
    return findings

total = 0
for f in FILES:
    findings = scan(f)
    # 只保留看起来像"被逗号撕开的句子片段"的键:含中文且含数字/单位/标点
    suspicious = [x for x in findings if not x[1].isascii()]
    print('=' * 72)
    print(f'FILE: {f}   可疑键(非 ASCII 键)={len(suspicious)}')
    print('=' * 72)
    for lineno, key, path in suspicious:
        print(f'  L{lineno:<5} key={key!r}')
        print(f'         at {path}')
    total += len(suspicious)
    print()
print(f'合计可疑键: {total}')
