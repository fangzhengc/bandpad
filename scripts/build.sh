#!/usr/bin/env bash
# 一键构建：手环端 RPK + 手机端 APK，产物统一落到 dist/
#
# 工具链来源（按优先级自动探测）：
#   1. 仓库内 .toolchain/（可选，作者本机用的免安装版 JDK+SDK+Gradle，已 .gitignore）
#   2. 系统环境：JAVA_HOME + ANDROID_HOME/ANDROID_SDK_ROOT + PATH 里的 gradle
#
# 首次编译前请先跑 bash scripts/gen-pem.sh 生成两端共用的签名，否则产物是 debug 签名，
# 与已发布的 RPK 不匹配，手环通道会报签名错误。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ── 工具链探测 ─────────────────────────────────────────────────────
if [ -d "$ROOT/.toolchain/jdk-17.0.2" ]; then
  export JAVA_HOME="$ROOT/.toolchain/jdk-17.0.2"
  echo "JDK：$JAVA_HOME（仓库内）"
fi
if [ -d "$ROOT/.toolchain/android-sdk" ]; then
  export ANDROID_HOME="$ROOT/.toolchain/android-sdk"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  echo "SDK：$ANDROID_HOME（仓库内）"
fi
[ -n "${ANDROID_SDK_ROOT:-}" ] || export ANDROID_SDK_ROOT="${ANDROID_HOME:-}"

if [ -x "$ROOT/.toolchain/gradle-8.5/bin/gradle" ]; then
  GRADLE="$ROOT/.toolchain/gradle-8.5/bin/gradle"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="gradle"
else
  echo "找不到 gradle：装一个，或把绿色版放到 .toolchain/gradle-8.5/" >&2
  exit 1
fi

if [ -z "${JAVA_HOME:-}" ] && ! command -v java >/dev/null 2>&1; then
  echo "找不到 JDK 17：设置 JAVA_HOME 或把它装进 PATH" >&2
  exit 1
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  echo "找不到 Android SDK：设置 ANDROID_HOME" >&2
  exit 1
fi

# Android Gradle Plugin 要读 local.properties，没有就按环境变量生成一份
if [ ! -f "$ROOT/phone/local.properties" ]; then
  echo "sdk.dir=$ANDROID_SDK_ROOT" > "$ROOT/phone/local.properties"
  echo "已生成 phone/local.properties（sdk.dir=$ANDROID_SDK_ROOT）"
fi

if [ ! -f "$ROOT/phone/keystore.properties" ]; then
  echo
  echo "⚠️  未找到 phone/keystore.properties：本次会用 debug 签名。"
  echo "   如果要发布或与现有 RPK 配套，请先跑 bash scripts/gen-pem.sh"
  echo
fi

mkdir -p "$ROOT/dist"

# ── 手环端 ─────────────────────────────────────────────────────────
echo "== 1/2 构建手环端 RPK =="
cd "$ROOT/band"
if [ ! -d node_modules/aiot-toolkit ]; then
  echo "安装手环端依赖（首次较慢）…"
  npm install --ignore-scripts --no-bin-links --no-audit --no-fund \
    --registry=https://registry.npmmirror.com
fi
npm run release
cp -f dist/*.rpk "$ROOT/dist/" 2>/dev/null || true

# ── 手机端 ─────────────────────────────────────────────────────────
echo "== 2/2 构建手机端 APK =="
cd "$ROOT/phone"
"$GRADLE" assembleRelease --no-daemon
cp -f app/build/outputs/apk/release/app-release.apk "$ROOT/dist/bandpad.apk" 2>/dev/null || true

echo
echo "== 产物 =="
ls -lh "$ROOT/dist"
