#!/usr/bin/env node
/**
 * 数据库操作拦截钩子(PreToolUse,Bash/PowerShell)
 *
 * ==========================================================================
 * 为什么存在
 * --------------------------------------------------------------------------
 * 2026-10-07 的数据事故:集成测试连的是**用户正在用的演示库 `tile_ai`**,
 * 其中若干测试做无条件全表删除,跑一次全量测试就删掉了用户的真实数据
 * (门店、报价参数、推荐搭配)。
 *
 * 根治手段是「测试库隔离」(见 tile-ai-server/pom.xml 的 surefire 配置 +
 * 只有 tile_ai_test 权限的受限账号)。**本钩子不是那道防线,是最后一道减速带** ——
 * 它的价值在于:在"要动用户数据"这个动作**发生之前**把它拦下来,让人有机会想一秒。
 *
 * ⚠️ **本钩子 fail-open**:脚本超时或崩溃时命令会照常执行。
 *    真正的保证是数据库授权与测试内护栏,不是这里。
 *
 * ==========================================================================
 * 分档规则(用户 2026-10-07 拍板:分档)
 * --------------------------------------------------------------------------
 *   deny —— 会**改写**用户库 `tile_ai` 的命令(删/改/建/清空)
 *   ask  —— 会**跑测试**的命令(测试曾经是删数据的元凶)
 *   放行 —— 其余一切,尤其**只读查询**(排查问题要看库,全拦死会让工具没法用)
 *
 * 关键实现细节:`\btile_ai\b` 里的 `_` 是词字符,所以它**不会**误命中
 * `tile_ai_test`(测试库)。这条是刻意的 —— 否则整个钩子会拦自己的测试。
 */

'use strict'

const DENY = (reason) => emit('deny', reason)
const ASK = (reason) => emit('ask', reason)

function emit(decision, reason) {
  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: 'PreToolUse',
        permissionDecision: decision,
        permissionDecisionReason: reason,
      },
    }),
  )
  process.exit(0)
}

/** 读 stdin 的全部内容。 */
function readStdin() {
  try {
    return require('fs').readFileSync(0, 'utf8')
  } catch {
    return ''
  }
}

/**
 * 去掉开头的 BOM 与首尾空白。
 *
 * ⚠️ **这条不是洁癖,是必备**:Windows 上 PowerShell 往管道里写时会**加一个 BOM**
 * (`﻿`),而 `JSON.parse` 遇到它会直接抛错。抛错会被下面的兜底吞掉,
 * 结果是**钩子静默失效、看起来完全正常** —— 2026-10-07 首次管道测试就是这么
 * 全部"放行"的,幸好测了。这类"装上了却没生效"正是本项目最警惕的东西。
 */
function cleanJson(text) {
  return text.replace(/^﻿/, '').trim()
}

function main() {
  let payload
  try {
    payload = JSON.parse(cleanJson(readStdin()))
  } catch {
    process.exit(0) // 读不到 / 不是 JSON —— 静默放行,不制造噪音
  }

  const command = String((payload.tool_input && payload.tool_input.command) || '')
  if (!command) process.exit(0)

  const cmd = command.replace(/\s+/g, ' ')

  // ------------------------------------------------------------------
  // ① deny:改写用户库
  // ------------------------------------------------------------------
  // `\b` 两侧夹住,`tile_ai_test` 不会命中(_ 是词字符)
  const touchesUserDb = /\btile_ai\b/.test(cmd)
  const mutating = /\b(delete|drop|truncate|update|insert|alter|replace|rename)\b/i.test(cmd)
  const createDb = /\bcreate\s+database\b/i.test(cmd)
  // **必须真的在调用数据库客户端**。没有这一条,"正文里恰好同时出现 tile_ai 与
  // delete 两个字"就会误伤(例如 `rm -rf` 一个名字里带 tile_ai 的目录、
  // 或者 grep 日志)。`mysqldump` 不会被 `\bmysql\b` 命中(`d` 是词字符),
  // 所以备份命令不受影响 —— 那是对的,备份不该被拦。
  const dbClient = /\b(mysql|mariadb|mysqladmin)\b/i.test(cmd)

  if (dbClient && touchesUserDb && (mutating || createDb)) {
    DENY(
      '拒绝对用户数据库 tile_ai 的写操作。这是你正在使用的业务库,测试请用 tile_ai_test' +
        '(集成测试已由 pom.xml 的 surefire 配置自动指向它)。' +
        '如果这确实是一次有意的运维操作,请你自己在终端里执行,不要通过我。' +
        '⚠️ 如果你只是在**写文档或注释**、正文里引用了一条这样的命令,请改用文件写入工具' +
        '(本钩子只检查 shell 命令)。',
    )
  }

  // ------------------------------------------------------------------
  // ② ask:跑测试
  // ------------------------------------------------------------------
  const skipTests = /-DskipTests|-Dmaven\.test\.skip=true/.test(cmd)
  const mvnTest = /\bmvn\b/.test(cmd) && /\b(test|verify|integration-test)\b/.test(cmd)
  const pytest = /\bpytest\b/.test(cmd)
  const npmTest = /\bnpm\s+(run\s+)?test\b/.test(cmd)
  // contract-diff.sh 内部会跑 mvn test(CI 里也是),ci.sh 是纯静态校验、不连库
  const contractDiff = /contract-diff\.sh/.test(cmd)

  if (!skipTests && (mvnTest || pytest || npmTest || contractDiff)) {
    ASK(
      '这条命令会跑测试。集成测试现在已隔离到 tile_ai_test(受限账号 tile_test,' +
        '对 tile_ai 无任何权限),但跑测试仍会改动测试库与 Redis 第 15 库。继续?',
    )
  }

  // ------------------------------------------------------------------
  // ③ 其余:静默放行(只读查询走这条)
  // ------------------------------------------------------------------
  process.exit(0)
}

try {
  main()
} catch {
  // 任何内部异常一律放行 —— 绝不 exit 1(那只是"非阻塞错误",命令照样跑,却会刷日志)
  process.exit(0)
}
