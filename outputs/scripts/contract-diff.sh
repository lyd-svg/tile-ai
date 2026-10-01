#!/usr/bin/env bash
# =============================================================================
# 契约 ↔ 实现比对 —— 一条命令跑两侧
#
# 用途:量一次「实现和契约对不对得上」。**新增或改动接口后必须跑一次**
#       (规则见 CLAUDE.md §三)。两侧各出一份可读清单 + 一份机器可读结论。
#
# 比对范围:路径 + HTTP 方法 + 字段名 + **类型** + **必填(仅请求体)** + **枚举取值**。
# 覆盖不到:format 与取值约束(minLength / minimum…) · 状态码与业务错误码 ·
#          query / path / header 参数名 · 幂等与状态机 · 嵌套深度 > 5 ·
#          Map 与 additionalProperties · 运行期改写 · SSE 事件集。
#          完整口径、豁免与「它抓不到什么」见 openapi-verification.md §36 与 §40。
#
# 用法:
#   bash outputs/scripts/contract-diff.sh            # 出清单,并因分叉判红(默认)
#   GATE=0 bash outputs/scripts/contract-diff.sh     # 只出清单,不判红
#   CONTRACT_DIFF_SIDES=java bash outputs/scripts/contract-diff.sh   # 只跑 Java 侧
#
# **默认 GATE=1(2026-10-01 起)**:本脚本的语义本就是「量一次」,量完不判等于白量。
# 无论 GATE 取值,下面三类**永远判红**(它们是"工具自己不可信"或"清单在漂移",不是分叉):
#   「锚点未命中」「挂账已过期(整条)」「挂账已过期(字段级)」。
#
# 退出码:0 = 跑到的侧都无红项;1 = 有红项;2 = 工具缺失(环境没配好)
#
# 报告落点(已 gitignore,可随时重新生成):
#   outputs/reports/contract-impl-diff.md          Java 侧 · 给人看
#   outputs/reports/contract-impl-diff.json        Java 侧 · 给 check_t.py 的 T-12 看
#   outputs/reports/contract-impl-diff-python.md   Python 侧 · 给人看
#
# 环境变量(CI 用):
#   CONTRACT_DIFF_RUN_ID  本次运行标识,会被写进 json;check_t.py 核它防"拿旧产物当本轮"
#   CONTRACT_DIFF_SIDES   逗号分隔,默认 java,python;CI 传 java(那边没装 fastapi 依赖)
#
# **不进 ci.sh**:CI 的契约门禁只有 Node + Python,没有 JDK,跑不了 Java 侧。
#   但 T-12 已改为读本脚本产出的 json —— 见 check_t.py 与 .github/workflows/contract-lint.yml,
#   那里会在 ci.sh **之前**先跑本脚本(docs: openapi-verification.md §40)。
# =============================================================================

set -uo pipefail   # 故意不用 -e:两侧都要跑完再汇总,而非首侧失败即中断

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REPORTS="$ROOT/outputs/reports"
SERVER="$ROOT/tile-ai-server"
AGENT="$ROOT/tile-ai-agent"

JAVA_REPORT="$REPORTS/contract-impl-diff.md"
JAVA_JSON="$REPORTS/contract-impl-diff.json"
PY_REPORT="$REPORTS/contract-impl-diff-python.md"
JAVA_LOG="$REPORTS/contract-diff-java.log"
PY_LOG="$REPORTS/contract-diff-python.log"

# 默认判红;留 GATE=0 时退回到"只出清单"的体检模式。
GATE="${GATE:-1}"

# 跑哪几侧。CI 只跑 java:runner 上没有装 fastapi / pydantic,跑到 Python 侧必红。
SIDES="${CONTRACT_DIFF_SIDES:-java,python}"
RUN_JAVA=0
RUN_PY=0
case ",$SIDES," in *,java,*)   RUN_JAVA=1 ;; esac
case ",$SIDES," in *,python,*) RUN_PY=1   ;; esac

# 新鲜度锚点:写进 json,check_t.py 核对它,免得把上一轮的产物当成本轮结果。
# 必须 export —— Java 通过 System.getenv 读它。
export CONTRACT_DIFF_RUN_ID="${CONTRACT_DIFF_RUN_ID:-local}"

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
rm -f "$JAVA_REPORT" "$JAVA_JSON" "$PY_REPORT"

# =============================================================================
# Java 侧:openapi-c + openapi-admin + openapi-internal 中 x-direction 归 Java 的
# =============================================================================

if [ "$RUN_JAVA" -eq 1 ]; then
  title "Java 侧(C 端 / admin / internal 归 Java 的)"

  if [ -z "${JAVA_HOME:-}" ] && [ -d "$JDK_HOME_HINT" ]; then
    export JAVA_HOME="$JDK_HOME_HINT"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi

  # 空数组在 bash 5 下安全展开;bash < 4.4 才会因 set -u 报错。
  MVN_ARGS=()
  [ -f "$MVN_SETTINGS" ] && MVN_ARGS+=(-s "$MVN_SETTINGS")
  if [ "$GATE" = "1" ]; then
    MVN_ARGS+=(-Dcontract.diff.gate=true)
  fi

  if ! command -v mvn >/dev/null 2>&1; then
    printf '%s\n' "✗ 找不到 mvn —— 本机在 D:\\dev-tools\\maven\\bin,需先加进 PATH"
    EXIT=2
  else
    [ -f "$MVN_SETTINGS" ] || printf '%s\n' "⚠ 未找到 $MVN_SETTINGS,将以 Maven 默认配置运行"
    printf '%s\n' "运行中(首次可能较慢)…"
    # 输出落日志:这里是"跑一次量一次"的工具,清单比控制台刷屏重要。
    # 带上 ImplReaderShapeTest:它是「提取器没读空」的负向对照 ——
    # 只跑比较器的话,提取器静默失灵时报告照样全绿。
    if ( cd "$SERVER" \
         && mvn "${MVN_ARGS[@]}" test '-Dtest=ContractImplDiff*Test,ImplReaderShapeTest' ) \
         >"$JAVA_LOG" 2>&1; then
      printf '%s\n' "✓ 通过 —— $(grep -aE 'Tests run:' "$JAVA_LOG" | tail -1 | tr -s ' ')"
      printf '%s\n' "  清单:$(rel "$JAVA_REPORT") · 结论:$(rel "$JAVA_JSON")"
    else
      printf '%s\n' "✗ 未通过(详见 $(rel "$JAVA_LOG"))"
      grep -aE 'Tests run:|BUILD FAILURE|\[ERROR\]' "$JAVA_LOG" | head -20
      EXIT=1
    fi
  fi
else
  printf '\n%s\n' "Java 侧已按 CONTRACT_DIFF_SIDES=$SIDES 跳过。"
fi

# =============================================================================
# Python 侧:openapi-internal 中 x-direction 归 Python 的
# =============================================================================

if [ "$RUN_PY" -eq 1 ]; then
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
    if ( cd "$AGENT" && CONTRACT_DIFF_GATE="$GATE" "$PY" -m pytest tests/test_contract_diff.py -q ) \
         >"$PY_LOG" 2>&1; then
      printf '%s\n' "✓ 通过 —— $(tail -1 "$PY_LOG")"
      printf '%s\n' "  清单:$(rel "$PY_REPORT")"
    else
      printf '%s\n' "✗ 未通过(详见 $(rel "$PY_LOG"))"
      tail -30 "$PY_LOG"
      EXIT=1
    fi
  fi
else
  printf '\n%s\n' "Python 侧已按 CONTRACT_DIFF_SIDES=$SIDES 跳过。"
fi

# =============================================================================
# 汇总
# =============================================================================

title "汇总"

MISSING=0
if [ "$RUN_JAVA" -eq 1 ]; then
  for report in "$JAVA_REPORT" "$JAVA_JSON"; do
    if [ -f "$report" ]; then
      printf '%s\n' "产物已生成:$(rel "$report")"
    else
      printf '%s\n' "产物缺失  :$(rel "$report")(该侧没跑到出产物那一步)"
      [ "$EXIT" -eq 0 ] && EXIT=1
      MISSING=1
    fi
  done
fi
if [ "$RUN_PY" -eq 1 ]; then
  if [ -f "$PY_REPORT" ]; then
    printf '%s\n' "产物已生成:$(rel "$PY_REPORT")"
  else
    printf '%s\n' "产物缺失  :$(rel "$PY_REPORT")(该侧没跑到出清单那一步)"
    [ "$EXIT" -eq 0 ] && EXIT=1
    MISSING=1
  fi
fi
[ "$MISSING" -eq 0 ] && [ "$EXIT" -ne 2 ] && printf '%s\n' "两侧产物齐备。"

printf '\n'
if [ "$GATE" = "1" ]; then
  printf '%s\n' "GATE=1(默认):分叉已被判成红,红项见上面各侧的输出。"
else
  printf '%s\n' "GATE=0:只出清单、不因分叉判红。要看红: bash outputs/scripts/contract-diff.sh"
fi

if [ "$EXIT" -eq 0 ]; then
  printf '%s\n' "结论:跑到的侧都干净。清单里「无法比对」那几行要人看 —— 跳过 ≠ 通过。"
elif [ "$EXIT" -eq 2 ]; then
  printf '%s\n' "结论:环境没配好(退出码 2)—— 这不是「契约有问题」,别读成红项。"
else
  printf '%s\n' "结论:有红项(退出码 $EXIT)。"
fi

exit "$EXIT"
