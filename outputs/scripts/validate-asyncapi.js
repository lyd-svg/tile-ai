// =============================================================================
// AsyncAPI 契约校验(G-1 / D-13)
//
// 三层校验:
//   ① 结构合法性       —— AsyncAPI 3.x 规范
//   ② 跨文件 $ref 解析 —— 能解析到 openapi-common.yaml 里的真实 schema
//   ③ 语义断言         —— 幂等键、方向标注、消息体与 channel 的挂载关系
//
// 第 ③ 层是关键:前两层只能证明"文档合法",证明不了"契约完整"。
// 例如把 idempotency key 字段删掉,文档依然合法,但契约已经废了。
//
// 退出码:0 = 全部通过;1 = 存在错误
// =============================================================================
const { Parser } = require('@asyncapi/parser');
const path = require('path');
const fs = require('fs');

const ROOT = path.resolve(__dirname, '..', '..');
const SPEC = path.join(ROOT, 'asyncapi-internal.yaml');

let failed = 0;
const fail = (msg) => { failed++; console.log(`  ✗ ${msg}`); };
const pass = (msg) => console.log(`  ✓ ${msg}`);

(async () => {
  console.log('='.repeat(72));
  console.log('AsyncAPI 契约校验: asyncapi-internal.yaml');
  console.log('='.repeat(72));

  if (!fs.existsSync(SPEC)) {
    fail(`找不到 ${SPEC}`);
    process.exit(1);
  }

  // ---------------------------------------------------------------- ①② 解析
  const parser = new Parser();
  const { document, diagnostics } = await parser.parse(fs.readFileSync(SPEC, 'utf8'), {
    source: SPEC,
  });

  const errs = (diagnostics || []).filter((d) => d.severity === 0);
  const warns = (diagnostics || []).filter((d) => d.severity === 1);

  console.log('\n[1] 解析与诊断');
  if (diagnostics && diagnostics.length) {
    for (const d of diagnostics) {
      const tag = d.severity === 0 ? 'error' : d.severity === 1 ? 'warn' : 'hint';
      console.log(`    [${tag}] ${d.code}: ${d.message}`);
    }
  } else {
    console.log('    (无诊断信息)');
  }
  errs.length === 0 ? pass('AsyncAPI 3.x 结构合法') : fail(`结构校验有 ${errs.length} 个错误`);

  if (!document) {
    console.log('\n结论: 无法解析,后续断言跳过。');
    process.exit(1);
  }

  // ------------------------------------------------ ③ 语义断言
  console.log('\n[2] 跨文件 $ref 解析(复用 openapi-common.yaml,避免多份真源)');

  // 取回被引用的 OpenAPI schema,确认不是"解析通过但没解析到"
  const refChecks = [
    { label: 'Part 枚举', want: ['floor', 'wall', 'local'] },
    { label: 'TaskType 枚举', want: ['image', 'parse'] },
  ];
  const openapiCommon = JSON.parse(
    JSON.stringify(
      // 直接用 yaml 解析太重,这里只断言解析器能取回 schema 即可
      {}
    )
  );

  for (const rc of refChecks) {
    // 通过 payload schema 的 actual schema 取回
    let found = null;
    for (const chName of document.channels().all().map((c) => c.id())) {
      const ch = document.channels().get(chName);
      for (const m of ch.messages().all()) {
        const j = m.payload() && m.payload().json();
        const s = JSON.stringify(j);
        if (rc.want.every((v) => s.includes(`"${v}"`))) found = rc.label;
      }
    }
    found ? pass(`${rc.label} 已通过 $ref 正确解析`) : fail(`${rc.label} 未能解析`);
  }

  console.log('\n[3] 语义断言(文档合法 ≠ 契约完整)');

  // 3.1 每类消息必须在内
  const REQUIRED_CHANNELS = ['queue.generate', 'queue.index.sync'];
  const addresses = document.channels().all().map((c) => c.address());
  for (const rq of REQUIRED_CHANNELS) {
    addresses.includes(rq)
      ? pass(`队列 ${rq} 已定义`)
      : fail(`缺少队列 ${rq}(架构 §6.2 要求)`);
  }
  console.log(`    已定义地址: ${addresses.join(', ')}`);

  // 3.2 每个 operation 必须标注生产方/消费方
  //     本契约描述两个服务之间的共享契约,不标方向则读者无法判断谁发谁收。
  let dirOk = true;
  for (const op of document.operations().all()) {
    const j = op.json();
    if (!j['x-producer'] || !j['x-consumer']) {
      fail(`operation "${op.id()}" 缺少 x-producer / x-consumer 方向标注`);
      dirOk = false;
    }
  }
  if (dirOk) pass('所有 operation 均标注了生产方与消费方');

  // 3.3 幂等键必须存在且被文档说明
  //     架构 §6.4 要求"消费端幂等";幂等键是这条要求的落点,删掉即契约失效。
  const IDEMPOTENCY = [
    { schema: 'GenerateTaskPayload', key: 'taskId', why: '生成任务以 taskId 幂等(架构 §6.4)' },
    { schema: 'IndexSyncPayload', key: 'eventId', why: '索引同步以 eventId 幂等(架构 §6.4)' },
    { schema: 'DlqPayload', key: 'originalMessage', why: '死信需保留原始消息以便重投(架构 §6.4)' },
  ];
  const schemas = document.components().schemas().all();
  const schemaMap = new Map(schemas.map((s) => [s.id(), s.json()]));
  for (const c of IDEMPOTENCY) {
    const s = schemaMap.get(c.schema);
    if (!s) { fail(`缺少 schema ${c.schema}`); continue; }
    (s.required || []).includes(c.key)
      ? pass(`${c.schema}.${c.key} 为必填 —— ${c.why}`)
      : fail(`${c.schema} 未把 "${c.key}" 列为必填 —— ${c.why}`);
  }

  // 3.4 生成任务的 parts 必须允许部分部位(PRD §5.2.4 允许不选全)
  const gp = schemaMap.get('GenerateTaskPayload');
  if (gp) {
    const parts = (gp.properties || {}).parts || {};
    (parts.minItems === 1)
      ? pass('GenerateTaskPayload.parts 的 minItems=1(允许部分部位,PRD §5.2.4)')
      : fail('GenerateTaskPayload.parts 未约束 minItems=1');
  }

  // 3.5 DLQ 的 originalQueue 枚举必须覆盖所有源队列
  const dlq = schemaMap.get('DlqPayload');
  if (dlq) {
    const oq = ((dlq.properties || {}).originalQueue || {}).enum || [];
    const missing = REQUIRED_CHANNELS.filter((q) => !oq.includes(q));
    missing.length === 0
      ? pass('DlqPayload.originalQueue 覆盖全部源队列')
      : fail(`DlqPayload.originalQueue 缺少: ${missing.join(', ')}`);
  }

  // ---------------------------------------------------------------- 结论
  console.log('\n' + '-'.repeat(72));
  if (failed) {
    console.log(`结论: 存在 ${failed} 个问题 -> 退出码 1`);
    process.exit(1);
  }
  console.log('结论: AsyncAPI 契约校验通过。');
  if (warns.length) console.log(`(有 ${warns.length} 条警告,不阻断)`);
  process.exit(0);
})().catch((e) => {
  console.error('校验器异常:', e && e.message ? e.message : e);
  process.exit(1);
});
