#!/usr/bin/env bash
# 把各平台刚构建出的发布 jar 收进仓库根的 dist/<版本号>/，供手动分发。
#
# 【与 D:\e33chat-dist 的关系】仓库外的 D:\e33chat-dist 是历史发版归档，本脚本
# 不写它 —— 已交付的 jar 不该被后来的构建悄悄覆盖。这里的 dist/ 是工作区暂存，
# 进 .gitignore，不入库。
#
# 用法：先跑 tools/build_all.sh，再跑 bash tools/collect_jars.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="$(grep '^mod_version=' gradle.properties | cut -d= -f2 | tr -d '\r')"
DEST="$ROOT/dist/$VERSION"
mkdir -p "$DEST"

MATRIX="$(mktemp)"
python tools/verify_targets.py --matrix-out "$MATRIX" > /dev/null
# 不走 python print：Windows 的 stdout 会把 \n 翻译成 \r\n，bash 变量里就带上了 \r
TARGETS="$(grep -o '"target": "[^"]*"' "$MATRIX" | cut -d'"' -f4)"
rm -f "$MATRIX"

for t in $TARGETS; do
  found=0
  for jar in "platforms/$t/build/libs/"*.jar; do
    [ -e "$jar" ] || continue
    case "$jar" in *sources*) continue ;; esac
    cp "$jar" "$DEST/"
    found=1
  done
  [ "$found" = 1 ] || { echo "目标 $t 的 build/libs 里没有发布 jar —— 先跑 tools/build_all.sh"; exit 1; }
done

echo "已收集到 $DEST ："
ls -la "$DEST"
