#!/usr/bin/env node
/**
 * db-guard.js 的自测。
 *
 * 为什么要写成文件:验证钩子需要构造"含敏感词的命令串",而那些串**本身就会触发钩子** ——
 * 写在命令行里会被自己拦住。放进文件、用 `node 本文件` 调用,命令行就干净了。
 *
 * 用法:node .claude/hooks/db-guard.selftest.js
 * 退出码 0 = 全部符合预期。
 */

'use strict'

const { execFileSync } = require('child_process')
const path = require('path')

const GUARD = path.join(__dirname, 'db-guard.js')
const CLIENT = 'mysql' // 拆开拼,免得本文件自己触发钩子
const USER_DB = ['tile', 'ai'].join('_')
const TEST_DB = USER_DB + '_test'

/** 期望:`deny` / `ask` / `pass` */
const CASES = [
  {
    desc: '① 删除用户库的表',
    cmd: `${CLIENT} -uroot -p1 -D ${USER_DB} -e "DELETE FROM t_store;"`,
    want: 'deny',
  },
  {
    desc: '② 清空用户库的表',
    cmd: `${CLIENT} -uroot -p1 -D ${USER_DB} -e "TRUNCATE TABLE t_plan;"`,
    want: 'deny',
  },
  {
    desc: '③ 改用户库的一行',
    cmd: `${CLIENT} -uroot -p1 -D ${USER_DB} -e "UPDATE t_quote_param SET param_value=1;"`,
    want: 'deny',
  },
  {
    desc: '④ 读用户库(必须放行 —— 排查问题要看库)',
    cmd: `${CLIENT} -uroot -p1 -D ${USER_DB} -e "SELECT COUNT(*) FROM t_store;"`,
    want: 'pass',
  },
  {
    desc: '⑤ 备份用户库(必须放行)',
    cmd: `mysqldump -uroot -p1 ${USER_DB} > backup.sql`,
    want: 'pass',
  },
  {
    desc: '⑥ 写测试库(必须放行 —— 那是隔离后的家)',
    cmd: `${CLIENT} -utile_test -px -e "DELETE FROM ${TEST_DB}.t_store;"`,
    want: 'pass',
  },
  {
    desc: '⑦ 跑全量测试',
    cmd: 'mvn -s D:\\dev-tools\\maven-settings.xml test "-Ddb.verify=true"',
    want: 'ask',
  },
  {
    desc: '⑧ 跑测试但跳过(必须放行)',
    cmd: 'mvn -s D:\\dev-tools\\maven-settings.xml test -DskipTests',
    want: 'pass',
  },
  {
    desc: '⑨ pytest',
    cmd: 'python -m pytest -q',
    want: 'ask',
  },
  {
    desc: '⑩ 只是文本里提到库名与删除词、并没调用数据库客户端(必须放行)',
    cmd: `grep -n "DELETE" ${USER_DB}.log`,
    want: 'pass',
  },
  {
    desc: '⑪ 普通命令(必须放行)',
    cmd: 'git status',
    want: 'pass',
  },
]

let failed = 0
for (const c of CASES) {
  const stdin = JSON.stringify({ tool_name: 'PowerShell', tool_input: { command: c.cmd } })
  let out = ''
  try {
    out = execFileSync(process.execPath, [GUARD], { input: stdin, encoding: 'utf8' })
  } catch (e) {
    out = '<<GUARD CRASHED: ' + e.message + '>>'
  }
  let got = 'pass'
  if (out) {
    try {
      got = JSON.parse(out).hookSpecificOutput.permissionDecision
    } catch {
      got = '<<UNPARSEABLE OUTPUT: ' + out + '>>'
    }
  }
  const ok = got === c.want
  if (!ok) failed++
  console.log(`${ok ? 'OK  ' : 'FAIL'}  ${c.desc}\n        期望=${c.want} 实际=${got}`)
}

console.log('')
console.log(failed === 0 ? `全部 ${CASES.length} 条符合预期。` : `有 ${failed} 条不符合预期。`)
process.exit(failed === 0 ? 0 : 1)
