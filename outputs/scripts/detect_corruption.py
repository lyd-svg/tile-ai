# -*- coding: utf-8 -*-
"""
检测 YAML 流式映射(flow mapping)中「未加引号的中文逗号」导致的键值撕裂。
原理:YAML flow 上下文里 `,` 是分隔符。写
    description: 规格,如 750x1500mm
会解析成 description='规格' 和 '如 750x1500mm'=None 两个键。
用 yaml.compose() 拿节点行号,精确定位。
"""
import os, sys, io, glob, yaml

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))

# 用 glob 扫描**所有**契约文件,而非硬编码清单。
#
# 教训:本文件原先硬编码 4 份 OpenAPI。新增 asyncapi-internal.yaml 后,
# 同一处逗号撕裂注入进 AsyncAPI,**7 个门禁全部显示通过** ——
# 因为 G4 不扫它,而 AsyncAPI 解析器又不拦这类缺陷(它不校验 schema 对象的
# 键白名单,与 redocly 的 struct 规则不同)。改用 glob 后,今后新增任何
# openapi-*.yaml / asyncapi-*.yaml 都会自动纳入。
FILES = sorted(
    glob.glob(os.path.join(_ROOT, 'openapi-*.yaml'))
    + glob.glob(os.path.join(_ROOT, 'asyncapi-*.yaml'))
)

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
per_file = {}
for f in FILES:
    findings = scan(f)
    # 只保留看起来像"被逗号撕开的句子片段"的键:含中文且含数字/单位/标点
    suspicious = [x for x in findings if not x[1].isascii()]
    per_file[f] = len(suspicious)
    print('=' * 72)
    print(f'FILE: {os.path.relpath(f, _ROOT)}   可疑键(非 ASCII 键)={len(suspicious)}')
    print('=' * 72)
    for lineno, key, path in suspicious:
        print(f'  L{lineno:<5} key={key!r}')
        print(f'         at {path}')
    total += len(suspicious)
    print()
print(f'合计可疑键: {total}')

if total:
    print()
    print('CI 判据:存在逗号撕裂 -> 退出码 1')
    print('  这类缺陷 YAML 本身不报错,但会让 description 被静默截断并产生非法键。')
    print('  修复方式见 openapi-verification.md §8.4 F-1。')
    sys.exit(1)
sys.exit(0)
