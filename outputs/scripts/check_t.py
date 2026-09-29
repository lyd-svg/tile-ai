# -*- coding: utf-8 -*-
"""
T-1 ~ T-15 契约静态校验断言脚本
对应 openapi-verification.md §5 校验清单。
所有断言基于 YAML 解析结果,不做肉眼判断。
输出:人类可读 + JSON(outputs/reports/t-results.json)
"""
import sys, io, json, os, re, copy
import yaml

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
FILES = {
    'common':   'openapi-common.yaml',
    'c':        'openapi-c.yaml',
    'admin':    'openapi-admin.yaml',
    'internal': 'openapi-internal.yaml',
}

DOCS = {}
RAW = {}
for k, fn in FILES.items():
    p = os.path.join(ROOT, fn)
    with open(p, 'r', encoding='utf-8') as f:
        RAW[k] = f.read()
    DOCS[k] = yaml.safe_load(RAW[k])

RESULTS = []
def record(tid, title, passed, evidence, detail=None):
    RESULTS.append({
        'id': tid, 'title': title,
        'result': 'PASS' if passed else 'FAIL',
        'evidence': evidence,
        'detail': detail or [],
    })
    print(f"[{'PASS' if passed else 'FAIL'}] {tid}  {title}")
    for e in evidence if isinstance(evidence, list) else [evidence]:
        print(f"        {e}")
    print()

# ---------- 跨文件 $ref 解析 ----------
def _load_ref(ref, base):
    """返回 (解析到的文档键, 节点)。
    ref 形如 './openapi-common.yaml#/components/schemas/X' 或 '#/components/schemas/X'"""
    if ref.startswith('#'):
        key, frag = base, ref[1:]
    else:
        fpath, _, frag = ref.partition('#')
        key = os.path.basename(fpath).replace('.yaml', '').replace('openapi-', '')
        key = {'openapi-common': 'common'}.get(key, key)
    node = DOCS[key]
    for part in [p for p in frag.split('/') if p]:
        part = part.replace('~1', '/').replace('~0', '~')
        node = node[part]
    return key, node

def load_ref(ref, base):
    """只要节点"""
    return _load_ref(ref, base)[1]

def deref(node, base, seen=None):
    """把 $ref 递归展开(不处理循环)。
    关键:跨文件跳转后,base 必须切到目标文档,否则目标文档内的 '#/...' 会解析错。"""
    seen = seen or set()
    if isinstance(node, dict):
        if '$ref' in node:
            r = node['$ref']
            if r in seen:
                return node
            newbase, tgt = _load_ref(r, base)
            return deref(copy.deepcopy(tgt), newbase, seen | {r})
        return {k: deref(v, base, seen) for k, v in node.items()}
    if isinstance(node, list):
        return [deref(v, base, seen) for v in node]
    return node

def iter_operations(doc):
    for path, item in (doc.get('paths') or {}).items():
        for method, op in item.items():
            if method in ('get','post','put','patch','delete','head','options','trace'):
                yield path, method, op

# ============================================================
# T-1 所有响应体为 ApiResponse(code/message/traceId 必填)
# ============================================================
print('#' * 72)
print('# T-1  所有响应体为 ApiResponse(code/message/traceId 必填)')
print('#' * 72)
api_resp = load_ref('#/components/schemas/ApiResponse', 'common')
req_ok = sorted(api_resp.get('required', []))
t1_ok = req_ok == ['code','message','traceId']
t1_ev = [f"common.ApiResponse.required = {req_ok}",
         f"期望 = ['code', 'message', 'traceId']"]

# 遍历所有响应,检查是否引用 ApiResponse(允许 text/event-stream 例外)
t1_exceptions = []
t1_binary = []
t1_bad = []
for key in ('c','admin','internal'):
    doc = DOCS[key]
    for path, method, op in iter_operations(doc):
        for code, resp in (op.get('responses') or {}).items():
            if '$ref' in resp:
                continue
            for mtype, mt in (resp.get('content') or {}).items():
                sch = mt.get('schema', {})
                s = json.dumps(sch, ensure_ascii=False)
                if mtype == 'text/event-stream':
                    t1_exceptions.append(f"{key} {method.upper()} {path} -> {mtype}")
                elif mtype != 'application/json':
                    # 文件下载等非 JSON 载体,无法包 ApiResponse —— 设计使然
                    t1_binary.append(f"{key} {method.upper()} {path} [{code}] -> {mtype}")
                elif 'ApiResponse' not in s:
                    t1_bad.append(f"{key} {method.upper()} {path} [{code}] {mtype}")

t1_pass = t1_ok and not t1_bad
t1_ev.append(f"ApiResponse.required 校验 = {'通过' if t1_ok else '失败'}")
t1_ev.append(f"JSON 响应体中非 ApiResponse 的接口数 = {len(t1_bad)}")
t1_ev += [f"  违规: {x}" for x in t1_bad]
t1_ev += [f"  设计例外(SSE 流式,无法包 ApiResponse): {x}" for x in t1_exceptions]
t1_ev += [f"  设计例外(二进制文件下载,无法包 ApiResponse): {x}" for x in t1_binary]
record('T-1', '所有响应体为 ApiResponse(code/message/traceId 必填)', t1_pass, t1_ev)

# ============================================================
# T-2 所有业务错误码 ∈ common.ErrorCode enum
# ============================================================
print('#' * 72)
print('# T-2  所有业务错误码 ∈ common.ErrorCode enum')
print('#' * 72)
err_enum = set(load_ref('#/components/schemas/ErrorCode', 'common')['enum'])
# 从 description 中抽取形如 40004 的 5 位错误码提及
mentioned = set()
t2_detail = []
for key, raw in RAW.items():
    for m in re.finditer(r'\b(\d{5})\b', raw):
        mentioned.add(int(m.group(1)))
unknown = sorted(mentioned - err_enum)
t2_pass = not unknown
t2_ev = [f"ErrorCode enum = {sorted(err_enum)}",
         f"文档中出现的 5 位码 = {sorted(mentioned)}",
         f"不在 enum 中的码 = {unknown if unknown else '无'}"]
record('T-2', '所有业务错误码 ∈ common.ErrorCode enum', t2_pass, t2_ev)

# ============================================================
# T-3 分页响应 data 含 list/total/page/size
# ============================================================
print('#' * 72)
print('# T-3  分页响应 data 含 list/total/page/size')
print('#' * 72)
pd = load_ref('#/components/schemas/PageData', 'common')
pd_req = sorted(pd.get('required', []))
t3_ok = pd_req == ['list','page','size','total']
# 找出所有"分页响应" = data 里引用 PageData 的响应
t3_paginated = []
for key in ('c','admin'):
    for path, method, op in iter_operations(DOCS[key]):
        for code, resp in (op.get('responses') or {}).items():
            s = json.dumps(resp, ensure_ascii=False)
            if 'PageData' in s:
                t3_paginated.append(f"{key} {method.upper()} {path}")
t3_pass = t3_ok and t3_paginated
t3_ev = [f"PageData.required = {pd_req}",
         f"期望 = ['list', 'page', 'size', 'total']",
         f"引用 PageData 的分页响应数 = {len(t3_paginated)}"]
t3_ev += [f"  - {x}" for x in t3_paginated]
record('T-3', '分页响应 data 含 list/total/page/size', t3_pass, t3_ev,
       detail=t3_paginated)

# ============================================================
# T-4 需登录接口带 security: cookieAuth;公开接口 security: []
# ============================================================
print('#' * 72)
print('# T-4  需登录接口 security=cookieAuth;公开接口 security=[]')
print('#' * 72)
# 期望表来自 tile-ai-architecture.md §10.2 鉴权列
EXPECT_AUTH = {   # (path, method) -> 'login' | 'public' | 'temp'
    ('/auth/sms/send','post'):'public',
    ('/auth/login/sms','post'):'public',
    ('/auth/register/set-password','post'):'temp',
    ('/auth/login/password','post'):'public',
    ('/auth/password','post'):'login',
    ('/auth/logout','post'):'login',
    ('/auth/refresh','post'):'public',
    ('/products','get'):'public',
    ('/products/{sku}','get'):'public',
    ('/combos','get'):'public',
    ('/combos/{id}','get'):'public',
    ('/agent/chat','post'):'login',
    ('/agent/chat/sessions','get'):'login',
    ('/agent/chat/sessions/{id}','get'):'login',
    ('/layout/templates','get'):'public',
    ('/layout/parse','post'):'public',
    ('/generate','post'):'login',
    ('/generate/{taskId}','get'):'login',
    ('/quote/calc','post'):'public',
    ('/plans','get'):'login',
    ('/plans/{id}','get'):'login',
    ('/plans/{id}','patch'):'login',
    ('/plans/{id}','delete'):'login',
    ('/plans/{id}/copy','post'):'login',
    ('/plans/{id}/share','post'):'login',
    ('/plans/{id}/share','delete'):'login',
    ('/plans/{id}/recalc','post'):'login',
    ('/share/{token}','get'):'public',
    ('/store','get'):'public',
    ('/dicts','get'):'public',
}
t4_bad = []
t4_checked = 0
cdoc = DOCS['c']
for (path, method), want in EXPECT_AUTH.items():
    op = (cdoc.get('paths', {}).get(path) or {}).get(method)
    if op is None:
        t4_bad.append(f"缺失接口 {method.upper()} {path}")
        continue
    t4_checked += 1
    sec = op.get('security', None)
    if want == 'public':
        if sec != []:
            t4_bad.append(f"{method.upper()} {path}: 期望 security: [], 实际 {sec!r}")
    elif want == 'login':
        if sec != [{'cookieAuth': []}] and sec != ['cookieAuth']:
            t4_bad.append(f"{method.upper()} {path}: 期望 security: [cookieAuth], 实际 {sec!r}")
    elif want == 'temp':
        if not sec or 'tempToken' not in json.dumps(sec):
            t4_bad.append(f"{method.upper()} {path}: 期望 tempToken, 实际 {sec!r}")
# 反向:是否有契约里出现但期望表没有的接口(未覆盖)
extra = []
for path, method, op in iter_operations(cdoc):
    if (path, method) not in EXPECT_AUTH:
        extra.append(f"{method.upper()} {path}")
t4_pass = not t4_bad and not extra
t4_ev = [f"核对了 {t4_checked} 个接口(期望表来自架构 §10.2 鉴权列)",
         f"不符 = {t4_bad if t4_bad else '无'}",
         f"未在期望表中(=新增未登记) = {extra if extra else '无'}"]
record('T-4', '需登录接口 security=cookieAuth;公开接口 security=[]', t4_pass, t4_ev)

# ============================================================
# T-5 内部接口带 security: serviceToken
# ============================================================
print('#' * 72)
print('# T-5  内部接口带 security: serviceToken')
print('#' * 72)
idoc = DOCS['internal']
root_sec = idoc.get('security')
t5_bad = []
for path, method, op in iter_operations(idoc):
    eff = op.get('security', root_sec)
    if not eff or 'serviceToken' not in json.dumps(eff):
        t5_bad.append(f"{method.upper()} {path}: {eff!r}")
# 方案是否在本文件内定义(OpenAPI 要求同文档可见)
scheme_local = 'serviceToken' in (idoc.get('components', {}).get('securitySchemes') or {})
scheme_common = 'serviceToken' in (DOCS['common'].get('components', {}).get('securitySchemes') or {})
t5_pass = (not t5_bad) and scheme_local
t5_ev = [f"internal 根级 security = {root_sec}",
         f"未继承/未声明的接口 = {t5_bad if t5_bad else '无'}",
         f"serviceToken 在 internal.yaml 内定义 = {scheme_local}",
         f"serviceToken 在 common.yaml 内定义 = {scheme_common}"]
if not scheme_local and scheme_common:
    t5_ev.append("  ⚠️ 方案定义在 common.yaml,internal.yaml 未本地定义 —— "
                 "独立解析该文件时 security 悬空(redocly: security-defined)")
record('T-5', '内部接口带 security: serviceToken', t5_pass, t5_ev)

# ============================================================
# T-6 c/admin 契约不出现 /internal/* 路径
# ============================================================
print('#' * 72)
print('# T-6  c/admin 契约不出现 /internal/* 路径')
print('#' * 72)
t6_path_hits, t6_text_hits = [], []
for key in ('c','admin'):
    for path in (DOCS[key].get('paths') or {}):
        if path.startswith('/internal'):
            t6_path_hits.append(f"{key}: {path}")
    for m in re.finditer(r'/internal/[\w\-/{}]*', RAW[key]):
        line = RAW[key][:m.start()].count('\n') + 1
        t6_text_hits.append(f"{FILES[key]}:{line}  {m.group(0)}")
t6_pass = not t6_path_hits
t6_ev = [f"paths 中的 /internal/* = {t6_path_hits if t6_path_hits else '无'}",
         f"正文中字面出现 /internal/* 的位置(描述文本,非路径键) = {len(t6_text_hits)} 处"]
t6_ev += [f"  - {x}" for x in t6_text_hits]
record('T-6', 'c/admin 契约不出现 /internal/* 路径', t6_pass, t6_ev)

# ============================================================
# T-7 内部契约不出现 /internal/quote/*
# ============================================================
print('#' * 72)
print('# T-7  内部契约不出现 /internal/quote/*')
print('#' * 72)
t7_hits = [p for p in (idoc.get('paths') or {}) if p.startswith('/internal/quote')]
t7_text = [f"L{RAW['internal'][:m.start()].count(chr(10))+1}  {m.group(0)}"
           for m in re.finditer(r'/internal/quote[\w\-/]*', RAW['internal'])]
t7_pass = not t7_hits
t7_ev = [f"paths 中的 /internal/quote/* = {t7_hits if t7_hits else '无'}",
         f"正文提及 = {t7_text if t7_text else '无'}"]
record('T-7', '内部契约不出现 /internal/quote/*', t7_pass, t7_ev)

# ============================================================
# T-8 对外 schema 不出现 layout_enc;仅出现解密后 layout
# ============================================================
print('#' * 72)
print('# T-8  对外 schema 不出现 layout_enc(密文字段)')
print('#' * 72)
t8_bad = []
t8_layout_ok = []
for key in ('common','c','admin','internal'):
    def walk(n, path):
        if isinstance(n, dict):
            for k, v in n.items():
                if k == 'layout_enc':
                    t8_bad.append(f"{FILES[key]} :: {path}/layout_enc")
                if k == 'layout':
                    t8_layout_ok.append(f"{FILES[key]} :: {path}/layout")
                walk(v, f"{path}/{k}")
        elif isinstance(n, list):
            for i, v in enumerate(n):
                walk(v, f"{path}[{i}]")
    walk(DOCS[key], '')
t8_pass = not t8_bad
t8_ev = [f"出现 layout_enc 的次数 = {len(t8_bad)}", f"  {t8_bad if t8_bad else '无'}",
         f"出现明文 layout 字段的位置 = {len(t8_layout_ok)} 处"]
t8_ev += [f"  - {x}" for x in t8_layout_ok]
record('T-8', '对外 schema 不出现 layout_enc;仅出现解密后 layout', t8_pass, t8_ev)

# ============================================================
# T-9 Part enum 恒为 [floor, wall, local]
# ============================================================
print('#' * 72)
print('# T-9  Part enum 恒为 [floor, wall, local]')
print('#' * 72)
part = load_ref('#/components/schemas/Part', 'common')
t9_enum = part.get('enum')
t9_pass = t9_enum == ['floor','wall','local']
# 检查是否存在其它地方硬编码 part 取值
t9_inline = []
for key, raw in RAW.items():
    for m in re.finditer(r'floor/wall/local|floor, wall, local|\[floor, wall, local\]', raw):
        t9_inline.append(f"{FILES[key]}:{raw[:m.start()].count(chr(10))+1}")
t9_ev = [f"common.Part.enum = {t9_enum}",
         f"期望 = ['floor', 'wall', 'local']",
         f"正文中硬编码出现的部位枚举(描述文本) = {len(t9_inline)} 处"]
t9_ev += [f"  - {x}" for x in t9_inline]
record('T-9', 'Part enum 恒为 [floor, wall, local]', t9_pass, t9_ev)

# ============================================================
# T-10 TaskStatus 恒为 [pending, running, success, failed]
# ============================================================
print('#' * 72)
print('# T-10 TaskStatus 恒为 [pending, running, success, failed]')
print('#' * 72)
ts = load_ref('#/components/schemas/TaskStatus', 'common')
t10_enum = ts.get('enum')
t10_pass = t10_enum == ['pending','running','success','failed']
t10_other = []
for key in ('c','admin','internal'):
    def w(n, p):
        if isinstance(n, dict):
            if 'enum' in n and isinstance(n['enum'], list) and 'pending' in n['enum']:
                if n['enum'] != ['pending','running','success','failed']:
                    t10_other.append(f"{FILES[key]} :: {p} = {n['enum']}")
            for k, v in n.items():
                w(v, f"{p}/{k}")
        elif isinstance(n, list):
            for i, v in enumerate(n):
                w(v, f"{p}[{i}]")
    w(DOCS[key], '')
t10_ev = [f"common.TaskStatus.enum = {t10_enum}",
          f"期望 = ['pending', 'running', 'success', 'failed']",
          f"其它位置的同类 enum(如 ImportStatus.status) = {t10_other if t10_other else '无'}"]
record('T-10', 'TaskStatus 恒为 [pending, running, success, failed]', t10_pass, t10_ev)

# ============================================================
# T-11 §10.6 示例可被对应 schema 校验
# ============================================================
print('#' * 72)
print('# T-11 §10.6 示例(生成请求 / 回调 / 报价)可被对应 schema 校验')
print('#' * 72)
try:
    import jsonschema
    HAVE_JS = True
except ImportError:
    HAVE_JS = False

def oas_to_jsonschema(node):
    """把 OpenAPI 3.0 schema 转成 jsonschema 可用的形式:处理 nullable,去掉 x-*"""
    if isinstance(node, dict):
        out = {}
        for k, v in node.items():
            if k.startswith('x-'):
                continue
            if k == 'nullable':
                continue
            out[k] = oas_to_jsonschema(v)
        if node.get('nullable') and 'type' in out:
            t = out['type']
            out['type'] = [t, 'null'] if isinstance(t, str) else t
        return out
    if isinstance(node, list):
        return [oas_to_jsonschema(v) for v in node]
    return node

# --- 示例数据(摘自 tile-ai-architecture.md §10.6) ---
EX_GENERATE_REQ = {
    "space": "bathroom", "style": "modern", "angles": 1,
    "layout": {"spaces": [{"type": "bathroom", "length": 2.0, "width": 1.8, "height": 2.7,
                           "openings": [{"type": "door", "w": 0.8, "h": 2.1}]}]},
    "parts": [{"part": "floor", "sku": "TL-2043", "area": 3.6}]
}
EX_TASK_PROGRESS = {
    "taskId": "g_1027f3", "status": "success", "progress": 100, "planId": 12,
    "images": [{"url": "https://oss/x/a1.png", "view": "default", "isCover": True}]
}
EX_CALLBACK = {
    "taskId": "g_1027f3", "status": "success",
    "imageUrls": [{"url": "https://oss/.../a1.png", "view": "default", "isCover": True}],
    "progress": 100, "error": None, "traceId": "3f9c"
}
EX_QUOTE_REQ = {
    "space": "bathroom",
    "parts": [{"part": "floor", "sku": "TL-2043", "area": 3.6},
              {"part": "wall", "sku": "TL-3012", "area": 13.9}],
    "openings": [{"part": "wall", "openings": [{"type": "door", "w": 0.8, "h": 2.1},
                                               {"type": "window", "w": 1.0, "h": 1.2}]}]
}
EX_QUOTE_RES = {
    "materialTotal": 1817.01, "auxTotal": 292.40, "total": 2109.41,
    "items": [
        {"part": "floor", "sku": "TL-2043", "grossArea": 3.6, "effectiveArea": 3.6,
         "lossRate": 0.06, "pricePerSqm": 268.00, "materialFee": 1022.69},
        {"part": "wall", "sku": "TL-3012", "grossArea": 13.9, "effectiveArea": 11.02,
         "lossRate": 0.06, "pricePerSqm": 68.00, "materialFee": 794.32}],
    "rulesSnapshot": {"auxUnitPrice": 20.00, "lossRate": 0.06}
}

CASES = [
    ('生成请求 → c.GenerateRequest', EX_GENERATE_REQ,
     '#/components/schemas/GenerateRequest', 'c'),
    ('报价请求 → c.QuoteCalcRequest', EX_QUOTE_REQ,
     '#/components/schemas/QuoteCalcRequest', 'c'),
    ('任务进度 → c.TaskProgress', EX_TASK_PROGRESS,
     '#/components/schemas/TaskProgress', 'c'),
    ('报价响应 → common.QuoteResult', EX_QUOTE_RES,
     '#/components/schemas/QuoteResult', 'common'),
    ('生成回调 → internal.callback.requestBody', EX_CALLBACK,
     None, 'internal'),
]

t11_detail = []
t11_pass = True
if not HAVE_JS:
    t11_pass = False
    t11_detail.append('jsonschema 未安装,无法执行')
else:
    for label, inst, refpath, base in CASES:
        try:
            if refpath is None:
                sch = DOCS['internal']['paths']['/internal/generate/callback']['post'] \
                        ['requestBody']['content']['application/json']['schema']
            else:
                sch = load_ref(refpath, base)
            sch = deref(sch, base)
            sch = oas_to_jsonschema(sch)
            jsonschema.validate(instance=inst, schema=sch)
            t11_detail.append(f"  ✓ {label}")
        except Exception as e:
            t11_pass = False
            msg = str(e).split('\n')[0][:200]
            t11_detail.append(f"  ✗ {label} -> {msg}")
t11_ev = [f"用例数 = {len(CASES)}"] + t11_detail
record('T-11', '§10.6 示例可被对应 schema 校验', t11_pass, t11_ev)

# ============================================================
# T-12 实现导出的 OpenAPI 与本契约结构 diff
# ============================================================
print('#' * 72)
print('# T-12 实现导出的 OpenAPI 与本契约结构 diff(C、internal 双向)')
print('#' * 72)
impl_found = []
for pat in ('**/openapi.json', '**/openapi.yaml', '**/openapi.yml', '**/swagger.json'):
    import glob
    impl_found += glob.glob(os.path.join(ROOT, pat), recursive=True)
impl_found = [p for p in impl_found
              if os.path.basename(p) not in
              ('openapi-c.yaml','openapi-admin.yaml','openapi-common.yaml','openapi-internal.yaml')]
t12_pass = False
t12_ev = [
    "仓库内未发现任何实现导出的 OpenAPI 文档(Java springdoc / Python FastAPI 产物)",
    f"扫描结果 = {impl_found if impl_found else '无'}",
    "仓库内无任何实现代码(仅 .md / .yaml 文档)",
    "→ 该项**当前不可执行**(不可判 PASS/FAIL),属前置条件未满足,非契约缺陷",
]
record('T-12', '实现导出的 OpenAPI 与本契约结构 diff(双向)', t12_pass, t12_ev,
       detail=['不可执行:缺实现侧产物'])

# ============================================================
# T-13 仅 401/403 用真实 HTTP 状态,其余业务结果 HTTP 200
# ============================================================
print('#' * 72)
print('# T-13 仅 401/403 用真实 HTTP 状态,其余业务结果 HTTP 200')
print('#' * 72)
ALLOWED = {'200','401','403'}
t13_bad = []
t13_counts = {}
for key in ('c','admin','internal'):
    for path, method, op in iter_operations(DOCS[key]):
        for code in (op.get('responses') or {}):
            t13_counts[code] = t13_counts.get(code, 0) + 1
            if code not in ALLOWED:
                t13_bad.append(f"{FILES[key]} {method.upper()} {path}: {code}")
t13_pass = not t13_bad
t13_ev = [f"全部响应码分布 = {dict(sorted(t13_counts.items()))}",
          f"允许集 = {sorted(ALLOWED)}",
          f"越界响应码 = {t13_bad if t13_bad else '无'}"]
record('T-13', '仅 401/403 用真实 HTTP 状态,其余业务结果 HTTP 200', t13_pass, t13_ev)

# ============================================================
# T-14 所有 $ref 可解析(无悬空引用)
# ============================================================
print('#' * 72)
print('# T-14 所有 $ref 可解析(无悬空引用)')
print('#' * 72)
t14_bad = []
t14_total = 0
for key in FILES:
    def w(n, p):
        global t14_total
        if isinstance(n, dict):
            if '$ref' in n:
                t14_total += 1
                try:
                    load_ref(n['$ref'], key)
                except Exception as e:
                    t14_bad.append(f"{FILES[key]} :: {p} -> {n['$ref']} ({type(e).__name__})")
            for k, v in n.items():
                w(v, f"{p}/{k}")
        elif isinstance(n, list):
            for i, v in enumerate(n):
                w(v, f"{p}[{i}]")
    w(DOCS[key], '')
t14_pass = not t14_bad
t14_ev = [f"$ref 总数 = {t14_total}", f"悬空引用 = {t14_bad if t14_bad else '无'}"]
record('T-14', '所有 $ref 可解析(无悬空引用)', t14_pass, t14_ev)

# ============================================================
# T-15 space/style 不硬编码;内置 enum 为 GET /dicts 返回集子集
# ============================================================
print('#' * 72)
print('# T-15 space/style 不硬编码;内置 enum 为 /dicts 返回集子集')
print('#' * 72)
space_enum = load_ref('#/components/schemas/SpaceType', 'common')['enum']
style_enum = load_ref('#/components/schemas/StyleType', 'common')['enum']
# (a) /dicts 接口存在
c_paths = DOCS['c'].get('paths') or {}
a_paths = DOCS['admin'].get('paths') or {}
has_dicts_c = '/dicts' in c_paths
has_dicts_admin = '/dicts' in a_paths
# (b) Dict schema 存在且 type enum 覆盖 space/style
dict_sch = load_ref('#/components/schemas/Dict', 'common')
dict_types = dict_sch['properties']['type'].get('enum')
# (c) 是否存在把 space/style 硬编码成内联 enum 的地方(而非 $ref SpaceType/StyleType)
inline_enum = []
for key in ('c','admin','internal'):
    def w(n, p):
        if isinstance(n, dict):
            if 'enum' in n and isinstance(n['enum'], list):
                vals = set(n['enum'])
                if vals & set(space_enum) or vals & set(style_enum):
                    sp = json.dumps(n, ensure_ascii=False)
                    if 'SpaceType' not in sp and 'StyleType' not in sp:
                        inline_enum.append(f"{FILES[key]} :: {p} = {n['enum']}")
            for k, v in n.items():
                w(v, f"{p}/{k}")
        elif isinstance(n, list):
            for i, v in enumerate(n):
                w(v, f"{p}[{i}]")
    w(DOCS[key], '')
    # 也扫描 raw 文本里的内联 enum(flow / block 两种写法)
for key in ('c','admin','internal'):
    for m in re.finditer(r'enum:\s*\[[^\]]*\]', RAW[key]):
        vals = m.group(0)
        if any(v in vals for v in space_enum + style_enum):
            inline_enum.append(
                f"{FILES[key]}:{RAW[key][:m.start()].count(chr(10))+1}  {vals.strip()}")
t15_static_pass = has_dicts_c and has_dicts_admin and dict_types == ['space','style'] \
                  and not inline_enum and len(space_enum) > 0 and len(style_enum) > 0
t15_ev = [
    f"common.SpaceType.enum ({len(space_enum)}) = {space_enum}",
    f"common.StyleType.enum ({len(style_enum)}) = {style_enum}",
    f"C 端 GET /dicts 存在 = {has_dicts_c};B 端 /admin/dicts 存在 = {has_dicts_admin}",
    f"common.Dict.type.enum = {dict_types} (期望 ['space', 'style'])",
    f"把 space/style 硬编码为内联 enum 的位置 = {inline_enum if inline_enum else '无'}",
    "⚠️ 「内置 enum ⊆ GET /dicts 实际返回集」需**运行时数据**才能判定,静态不可证;",
    "   本项只验证其静态前置条件(字典接口与 schema 齐备、无硬编码)。",
]
record('T-15', 'space/style 不硬编码;内置 enum 为 /dicts 返回集子集', t15_static_pass, t15_ev)

# ============================================================
outdir = os.path.join(ROOT, 'outputs', 'reports')
os.makedirs(outdir, exist_ok=True)
with open(os.path.join(outdir, 't-results.json'), 'w', encoding='utf-8') as f:
    json.dump(RESULTS, f, ensure_ascii=False, indent=2)

print('#' * 72)
print('# 汇总')
print('#' * 72)
for r in RESULTS:
    print(f"  {r['id']:<5} {r['result']:<4} {r['title']}")
print()
print(f"PASS={sum(1 for r in RESULTS if r['result']=='PASS')}  "
      f"FAIL={sum(1 for r in RESULTS if r['result']=='FAIL')}  "
      f"共 {len(RESULTS)} 项")
print(f"结果已写入 {os.path.join(outdir, 't-results.json')}")
