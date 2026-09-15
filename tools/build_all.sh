#!/usr/bin/env bash
# 本地全端构建编排：先跑守卫闸，再逐 buildable 目标 clean+test+build，失败即停。
#
# 与 CI 的关系：CI（.github/workflows/build.yml）按 targets.json 的矩阵在三个
# runner 上并行构建；本脚本是同一件事的串行本地版，给推送前自检用。
# 目标清单不硬编码在这里 —— 读 versions/targets.json 里 buildable: true 的条目。
#
# 用法： bash tools/build_all.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# 1) 守卫闸：矩阵是规则、身份只有一份、孪生不缺不重。红了别构建，先修声明。
python tools/verify_targets.py

# 2) 目标清单由矩阵算出，不手抄。
MATRIX="$(mktemp)"
python tools/verify_targets.py --matrix-out "$MATRIX" > /dev/null
# 不走 python print：Windows 的 stdout 会把 \n 翻译成 \r\n，bash 变量里就带上了 \r
TARGETS="$(grep -o '"target": "[^"]*"' "$MATRIX" | cut -d'"' -f4)"
rm -f "$MATRIX"

# 3) 逐目标构建。Windows（Git Bash）走 gradlew.bat，其余走 gradlew。
case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*) GRADLE='cmd //c "gradlew.bat' ;;
  *) GRADLE='' ;;
esac

for t in $TARGETS; do
  echo "===== 构建目标 $t ====="
  (
    cd "platforms/$t"
    # clean 不是为了干净癖：Forge 端增量编译会静默弄丢 mixin refmap（见 build.gradle
    # 的 copyRefmap 注释），clean 是唯一被证明不会骗人的路径。
    ARGS="clean build -PrunTests --offline --console=plain -Dhttp.proxyHost= -Dhttps.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyPort="
    if [ -n "$GRADLE" ]; then
      eval "$GRADLE $ARGS\"" > _build.log 2>&1
    else
      ./gradlew $ARGS > _build.log 2>&1
    fi
  ) || { echo "目标 $t 构建失败，日志尾部："; tail -40 "platforms/$t/_build.log"; exit 1; }
  tail -2 "platforms/$t/_build.log"
done

echo
echo "全部目标构建通过。jar 位置："
for t in $TARGETS; do
  ls -1 "platforms/$t/build/libs/"*.jar 2>/dev/null | grep -v sources || true
done
