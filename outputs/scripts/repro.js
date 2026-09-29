// 最小复现:验证 swagger-parser v13 是否对普通 schema 属性误报
const SwaggerParser = require('@apidevtools/swagger-parser');

const doc = {
  openapi: '3.0.3',
  info: { title: 'repro', version: '1.0.0' },
  paths: {},
  components: {
    schemas: {
      PageData: {
        type: 'object',
        required: ['list', 'total', 'page', 'size'],
        properties: {
          list: { type: 'array', items: {} },
          total: { type: 'integer', description: '总条数' },
          page: { type: 'integer', description: '当前页,从 1 开始' },
          size: { type: 'integer', description: '每页条数' },
        },
      },
      Sku: {
        type: 'object',
        required: ['sku', 'name'],
        properties: {
          sku: { type: 'string', example: 'TL-2043' },
          spec: { type: 'string', example: '750x1500mm' },
        },
      },
    },
  },
};

(async () => {
  try {
    await SwaggerParser.validate(doc);
    console.log('[PASS] 最小复现通过 —— 说明原报错不是属性名本身导致的');
  } catch (e) {
    console.log('[FAIL] 最小复现也失败 —— 说明是 swagger-parser 对该写法的误报');
    console.log(e.message.split('\n').slice(0, 12).join('\n'));
  }
})();
