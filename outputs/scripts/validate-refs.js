// 用 @apidevtools/swagger-parser 做:$ref 解析(dereference)+ OpenAPI 3.0 合法性校验
const SwaggerParser = require('@apidevtools/swagger-parser');
const path = require('path');

const FILES = [
  'openapi-common.yaml',
  'openapi-c.yaml',
  'openapi-admin.yaml',
  'openapi-internal.yaml',
];

const ROOT = path.resolve(__dirname, '..', '..');

(async () => {
  let anyFail = false;
  for (const f of FILES) {
    const abs = path.join(ROOT, f);
    console.log('='.repeat(70));
    console.log(`FILE: ${f}`);
    console.log('='.repeat(70));
    try {
      // 1) parse:YAML 语法 + 读取
      const api = await SwaggerParser.parse(abs);
      console.log(`[PASS] parse (YAML 语法合法)`);
      console.log(`       openapi = ${api.openapi}`);

      // 2) validate:$ref 可解析 + 符合 OpenAPI 3.0 schema
      const validated = await SwaggerParser.validate(abs, { dereference: { circular: 'ignore' } });
      console.log(`[PASS] validate ($ref 全部可解析 + OpenAPI 3.0 结构合法)`);
      console.log(`       paths = ${Object.keys(validated.paths || {}).length}`);
      console.log(`       schemas = ${Object.keys((validated.components || {}).schemas || {}).length}`);
    } catch (e) {
      anyFail = true;
      console.log(`[FAIL] ${e.name || 'Error'}`);
      console.log(`       message: ${e.message}`);
      if (e.path && e.path.length) console.log(`       path: ${e.path.join(' > ')}`);
      if (e.source) console.log(`       source: ${e.source}`);
      if (e.details) console.log(`       details: ${JSON.stringify(e.details)}`);
    }
    console.log('');
  }
  console.log(anyFail ? 'OVERALL: 存在 FAIL' : 'OVERALL: 全部 PASS');
  process.exit(anyFail ? 1 : 0);
})();
