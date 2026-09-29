#!/usr/bin/env bash
# =============================================================================
# 契约静态校验 —— 统一 CI 入口
#
# 设计目标:把 4 份 OpenAPI 契约的全部校验收成**一个可判成败的命令**。
# 平台无关:Linux(GitHub Actions / GitLab CI)与 Windows(Git Bash)均可运行。
#
# 用法:
#   bash outputs/scripts/ci.sh              # 本地:缺依赖时才安装
#   CI=true bash outputs/scripts/ci.sh      # CI:强制 npm ci 干净安装
#
# 退出码:0 = 全部通过;1 = 存在失败的门禁
# 详细报告(机器可读):outputs/reports/
# =============================================================================

set -uo pipefail   # 故意不用 -e:要让所有门禁都跑完再汇总,而非首个失败即中断

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLS="$ROOT/outputs/tools"
REPORTS="$ROOT/outputs/reports"
BIN="$TOOLS/node_modules/.bin"
CONTRACTS=(openapi-common.yaml openapi-c.yaml openapi-admin.yaml openapi-internal.yaml)

mkdir -p "$REPORTS"
cd "$ROOT"

# validate-refs.js 等 Node 脚本位于 outputs/scripts/,而依赖装在
# outputs/tools/node_modules/ —— Node 的模块解析不会向上找到那里,
# 故用 NODE_PATH 显式指向,避免各调用点各自 cd 或写绝对 require。
export NODE_PATH="$TOOLS/node_modules"

# ---------------------------------------------------------------- 输出helpers
hr()    { printf '%s\n' "-------------------------------------------------------------"; }
title() { printf '\n'; hr; printf '%s\n' "$1"; hr; }

GATE_NAMES=()
GATE_RESULTS=()

# run_gate <slug> <显示名> <命令...>
# slug 必须是 ASCII —— 用它命名日志文件;中文名不能直接做文件名,否则
# 多个门禁的日志会因全部被转成 '-' 而互相覆盖。
run_gate() {
  local slug="$1" name="$2"; shift 2
  title "门禁: $name"
  local log="$REPORTS/ci-$slug.log"
  if "$@" 2>&1 | tee "$log"; then
    GATE_NAMES+=("$name"); GATE_RESULTS+=("PASS")
  else
    GATE_NAMES+=("$name"); GATE_RESULTS+=("FAIL")
  fi
}

# ---------------------------------------------------------------- 0. 环境检查
title "环境检查"
MISSING=0
for c in node npm python; do
  if command -v "$c" >/dev/null 2>&1; then
    printf '  %-8s %s\n' "$c" "$("$c" --version 2>&1 | head -1)"
  else
    printf '  %-8s 缺失\n' "$c"; MISSING=1
  fi
done
[ "$MISSING" -eq 0 ] || { echo "缺少必需命令,终止。"; exit 1; }

# ---------------------------------------------------------------- 1. 依赖安装
title "依赖安装"

if [ "${CI:-}" = "true" ] || [ ! -d "$TOOLS/node_modules" ]; then
  echo "  npm ci (按 package-lock.json 干净安装)..."
  npm ci --prefix "$TOOLS" --no-audit --no-fund --loglevel=error
  [ $? -eq 0 ] || { echo "npm ci 失败,终止。"; exit 1; }
else
  echo "  node_modules 已存在,跳过 (本地模式;设 CI=true 可强制干净安装)"
fi

if python -c "import yaml, jsonschema" >/dev/null 2>&1; then
  echo "  Python 依赖 (pyyaml / jsonschema) 已满足"
else
  echo "  安装 Python 依赖..."
  python -m pip install -q --disable-pip-version-check pyyaml jsonschema \
    || { echo "pip 安装失败,终止。"; exit 1; }
fi

# ---------------------------------------------------------------- 2. 各门禁
# G1  YAML 解析 + $ref 解析 + OpenAPI 3.0 结构校验(4 份)
run_gate "g1-struct" "G1-结构校验" \
  node "$ROOT/outputs/scripts/validate-refs.js"

# G2  Redocly(结构 + 规则;已知假阳见 redocly.yaml 注释)
redocly_all() {
  local rc=0
  for f in "${CONTRACTS[@]}"; do
    "$BIN/redocly" lint "$f" --format=stylish || rc=1
  done
  return $rc
}
run_gate "g2-redocly" "G2-redocly" redocly_all

# G3  Spectral(spectral:oas 规则集)
spectral_all() {
  local rc=0
  for f in "${CONTRACTS[@]}"; do
    "$BIN/spectral" lint "$f" --ruleset "$TOOLS/.spectral.yaml" || rc=1
  done
  return $rc
}
run_gate "g3-spectral" "G3-spectral" spectral_all

# G4  逗号撕裂扫描(YAML 流式映射中未加引号的逗号 -> 伪键 + 描述截断)
#     这是最隐蔽的一类缺陷:YAML 本身不报错,只有跑工具才暴露。
run_gate "g4-corruption" "G4-逗号撕裂" \
  python "$ROOT/outputs/scripts/detect_corruption.py"

# G5  描述完整性与全局伪键扫描
run_gate "g5-descriptions" "G5-描述完整性" \
  python "$ROOT/outputs/scripts/verify_descriptions.py"

# G6  T-1~T-15 断言(FAIL 阻断;SKIP 不阻断)
run_gate "g6-t1-t15" "G6-T1-T15" \
  python "$ROOT/outputs/scripts/check_t.py"

# ---------------------------------------------------------------- 3. 汇总
title "汇总"
FAILED=0
for i in "${!GATE_NAMES[@]}"; do
  printf '  %-18s %s\n' "${GATE_NAMES[$i]}" "${GATE_RESULTS[$i]}"
  [ "${GATE_RESULTS[$i]}" = "PASS" ] || FAILED=1
done

printf '\n  报告目录: %s\n' "$REPORTS"
hr

if [ "$FAILED" -ne 0 ]; then
  printf '\n结论: 校验未通过 —— 请修复上述 FAIL 门禁后重试。\n'
  printf '缺陷定位与修复指引见 openapi-verification.md §8。\n\n'
  exit 1
fi

printf '\n结论: 全部门禁通过。\n\n'
exit 0
