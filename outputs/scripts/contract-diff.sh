#!/usr/bin/env bash
# =============================================================================
# 契约 ↔ 实现比对 —— 一条命令跑两侧
#
# 用途:量一次「实现和契约对不对得上」。**新增或改动接口后必须跑一次**
#       (规则见 CLAUDE.md §三)。两侧各出一份可读清单,清单本身就是要看的东西。
#
# 比对范围:路径 + HTTP 方法 + 字段名。
# 覆盖不到:类型 / 必填 / 枚举值 · 状态码与业务错误码 · 幂等与状态机 ·
#          SSE 事件集(完全不解析)· 嵌套深度 > 2 · Map 型响应。
#          完整口径与豁免清单见 openapi-verification.md §36。
#
# 用法:
#   bash outputs/scripts/contract-diff.sh            # 只出清单(默认)
#   GATE=1 bash outputs/scripts/contract-diff.sh     # 顺带把「有分叉」判成红
#
# 默认不红是**故意的**:第一遍是体检不是治疗。但有两类无论如何都判红 ——
# 「锚点未命中」(工具自己没跑对)与「挂账已过期」(清单在漂移)。
#
# 退出码:0 = 两侧跑完且无红项;1 = 有红项;2 = 工具缺失(环境没配好)
#
# 报告落点(已 gitignore,可随时重新生成):
#   outputs/reports/contract-impl-diff.md         Java 侧
#   outputs/reports/contract-impl-diff-python.md  Python 侧
#
# **不进 ci.sh**:CI 只有 Node + Python,没有 JDK,跑不了 Java 侧。
# =============================================================================

set -uo pipefail   # 故意不用 -e:两侧都要跑完再汇总,而非首侧失败即中断

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REPORTS="$ROOT/outputs/reports"
AGENT="$ROOT/tile-ai-agent"

JAVA_REPORT="$REPORTS/contract-impl-diff.md"
PY_REPORT="$REPORTS/contract-impl-diff-python.md"
JAVA_LOG="$REPORTS/contract-diff-java.log"
PY_LOG="$REPORTS/contract-diff-python.log"

# Maven 本地仓库/镜像配置在仓库外(见 CLAUDE.md「动手前 / 后」)。
# 允许用环境变量覆盖,免得换台机器就要改脚本。
MVN_SETTINGS="${MVN_SETTINGS:-D:/dev-tools/maven-settings.xml}"
JDK_HOME_HINT="${JDK_HOME_HINT:-/d/dev-tools/jdk17}"

mkdir -p "$REPORTS"
cd "$ROOT"

# ---------------------------------------------------------------- 输出helpers
hr()    { printf '%s\n' "-------------------------------------------------------------"; }
title() { printf '\n'; hr; printf '%s\n' "$1"; hr; }

# 打印时去掉仓库根前缀 —— `pwd` 在 Git Bash 下是 /d/... 形态,
# 直接打印出来是本机专属的怪路径,读者认不出。
rel()   { printf '%s' "${1#"$ROOT"/}"; }

EXIT=0

# 上一轮的清单先删掉 —— 否则这一轮中途失败时,留下的旧清单会被
# 当成"刚跑出来的结果"看,这是最安静的假绿。
rm -f "$JAVA_REPORT" "$PY_REPORT"

# =============================================================================
# Java 侧:openapi-c + openapi-admin + openapi-internal 中 x-direction 归 Java 的
# =============================================================================

title "Java 侧(C 端 / admin / internal 归 Java 的)"

if [ -z "${JAVA_HOME:-}" ] && [ -d "$JDK_HOME_HINT" ]; then
  export JAVA_HOME="$JDK_HOME_HINT"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# 空数组在 bash 5 下安全展开;bash < 4.4 才会因 set -u 报错。
MVN_ARGS=()
[ -f "$MVN_SETTINGS" ] && MVN_ARGS+=(-s "$MVN_SETTINGS")
if [ "${GATE:-}" = "1" ]; then
  MVN_ARGS+=(-Dcontract.diff.gate=true)
fi

if ! command -v mvn >/dev/null 2>&1; then
  printf '%s\n' "✗ 找不到 mvn —— 本机在 D:\\dev-tools\\maven\\bin,需先加进 PATH"
  EXIT=2
else
  [ -f "$MVN_SETTINGS" ] || printf '%s\n' "⚠ 未找到 $MVN_SETTINGS,将以 Maven 默认配置运行"
  printf '%s\n' "运行中(首次可能较慢)…"
  # 输出落日志:这里是"跑一次量一次"的工具,清单比控制台刷屏重要。
  if ( cd "$ROOT/tile-ai-server" \
       && mvn "${MVN_ARGS[@]}" test -Dtest='ContractImplDiff*Test' ) \
       >"$JAVA_LOG" 2>&1; then
    printf '%s\n' "✓ 通过 —— $(grep -aE 'Tests run:' "$JAVA_LOG" | tail -1 | tr -s ' ')"
    printf '%s\n' "  清单:$(rel "$JAVA_REPORT")"
  else
    printf '%s\n' "✗ 未通过(详见 $(rel "$JAVA_LOG"))"
    grep -aE 'Tests run:|BUILD FAILURE|\[ERROR\]' "$JAVA_LOG" | head -20
    EXIT=1
  fi
fi

# =============================================================================
# Python 侧:openapi-internal 中 x-direction 归 Python 的
# =============================================================================

title "Python 侧(internal 归 Python 的)"

PY=""
for candidate in "$AGENT/.venv/Scripts/python.exe" "$AGENT/.venv/bin/python"; do
  [ -x "$candidate" ] && PY="$candidate" && break
done
if [ -z "$PY" ]; then
  # 没有 venv 就退到 PATH 上的 python —— 依赖可能不全,但不该因此什么都不跑
  PY="$(command -v python || command -v python3 || true)"
fi

if [ -z "$PY" ]; then
  printf '%s\n' "✗ 找不到 python —— 先在 tile-ai-agent 下建 .venv 并装 requirements.txt"
  EXIT=2
else
  printf '%s\n' "运行中…"
  # CONTRACT_DIFF_GATE 只认非空值(pytest 侧),与 Java 的开关分开读
  if ( cd "$AGENT" && CONTRACT_DIFF_GATE="${GATE:-}" "$PY" -m pytest tests/test_contract_diff.py -q ) \
       >"$PY_LOG" 2>&1; then
    printf '%s\n' "✓ 通过 —— $(tail -1 "$PY_LOG")"
    printf '%s\n' "  清单:$(rel "$PY_REPORT")"
  else
    printf '%s\n' "✗ 未通过(详见 $(rel "$PY_LOG"))"
    tail -30 "$PY_LOG"
    EXIT=1
  fi
fi

# =============================================================================
# 汇总
# =============================================================================

title "汇总"

for report in "$JAVA_REPORT" "$PY_REPORT"; do
  if [ -f "$report" ]; then
    printf '%s\n' "清单已生成:$(rel "$report")"
  else
    printf '%s\n' "清单缺失  :$(rel "$report")(该侧没跑到出清单那一步)"
    [ "$EXIT" -eq 0 ] && EXIT=1
  fi
done

if [ -z "${GATE:-}" ]; then
  printf '\n%s\n' "当前只出清单、不因分叉判红。要看红: GATE=1 bash outputs/scripts/contract-diff.sh"
else
  printf '\n%s\n' "GATE=1:分叉已被判成红。"
fi

if [ "$EXIT" -eq 0 ]; then
  printf '%s\n' "结论:两侧都跑完了。清单里「无法比对」那几行要人看 —— 跳过 ≠ 通过。"
else
  printf '%s\n' "结论:有红项(退出码 $EXIT)。"
fi

exit "$EXIT"
