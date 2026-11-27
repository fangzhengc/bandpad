#!/usr/bin/env bash
# 一键构建：手表端 RPK（可多个机型）+ 手机端 APK，产物统一落到 dist/
#
# 手表端是「一份源码 + 每个机型一套 manifest / platform 描述」：
#   band/manifest.9pro.json   + band/src/common/platform.js（默认就是 9 Pro）
#   band/manifest.watch6.json + band/src/common/platform.watch6.js
# 构建时用对应机型的文件临时覆盖 band/src/manifest.json 与 common/platform.js，打完自动还原。
# 以后加新机型：复制这两个文件改名，再在下面 BAND_PROFILES 里加一项即可。
#
# 用法：
#   bash scripts/build.sh                    # 手表端所有机型 + 手机端
#   bash scripts/build.sh --band-only        # 只出手表包
#   bash scripts/build.sh --phone-only       # 只出手机 APK
#   bash scripts/build.sh --profile watch6   # 只出某一个机型
#
# 工具链来源（按优先级自动探测）：
#   1. 仓库内 .toolchain/（可选，作者本机用的免安装版 JDK+SDK+Gradle，已 .gitignore）
#   2. 系统环境：JAVA_HOME + ANDROID_HOME/ANDROID_SDK_ROOT + PATH 里的 gradle
#
# 首次编译前请先跑 bash scripts/gen-pem.sh 生成两端共用的签名，否则产物是 debug 签名，
# 与已发布的 RPK 不匹配，手表通道会报签名错误。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BAND_PROFILES=(9pro watch6)

BUILD_BAND=1
BUILD_PHONE=1
ONLY_PROFILE=""
while [ $# -gt 0 ]; do
  case "$1" in
    --band-only) BUILD_PHONE=0 ;;
    --phone-only) BUILD_BAND=0 ;;
    --profile) shift; ONLY_PROFILE="${1:-}" ;;
    *) echo "未知参数：$1" >&2; exit 1 ;;
  esac
  shift
done

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

if [ "$BUILD_PHONE" -eq 1 ]; then
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
fi

mkdir -p "$ROOT/dist"

# ── 手表端：按机型分别出包 ──────────────────────────────────────────
band_deps() {
  cd "$ROOT/band"
  if [ ! -d node_modules/aiot-toolkit ]; then
    echo "安装手表端依赖（首次较慢）…"
    npm install --ignore-scripts --no-bin-links --no-audit --no-fund \
      --registry=https://registry.npmmirror.com
  fi
  cd "$ROOT"
}

build_band() {
  local profile="$1"
  local manifest_src="$ROOT/band/manifest.$profile.json"
  if [ ! -f "$manifest_src" ]; then
    echo "未知机型：$profile（缺 band/manifest.$profile.json）" >&2
    return 1
  fi

  echo "== 手表端：$profile =="
  cp -f "$manifest_src" "$ROOT/band/src/manifest.json"
  if [ -f "$ROOT/band/src/common/platform.$profile.js" ]; then
    cp -f "$ROOT/band/src/common/platform.$profile.js" "$ROOT/band/src/common/platform.js"
  fi

  ( cd "$ROOT/band" && npm run release )

  local ver rpk
  rpk=$(ls "$ROOT/band"/dist/*.rpk | head -1)
  ver=$(basename "$rpk" .rpk | sed 's/.*release\.//')
  cp -f "$rpk" "$ROOT/dist/$(basename "$rpk" .rpk).$profile.rpk"
  echo "   → dist/$(basename "$rpk" .rpk).$profile.rpk（$ver / $profile）"
}

if [ "$BUILD_BAND" -eq 1 ]; then
  band_deps
  # 备份会被覆盖的两个文件，无论成功失败都还原
  BAK_MANIFEST="$(mktemp)"
  BAK_PLATFORM="$(mktemp)"
  cp -f "$ROOT/band/src/manifest.json" "$BAK_MANIFEST"
  cp -f "$ROOT/band/src/common/platform.js" "$BAK_PLATFORM"
  trap 'cp -f "$BAK_MANIFEST" "$ROOT/band/src/manifest.json"; cp -f "$BAK_PLATFORM" "$ROOT/band/src/common/platform.js"; rm -f "$BAK_MANIFEST" "$BAK_PLATFORM"' EXIT

  if [ -n "$ONLY_PROFILE" ]; then
    build_band "$ONLY_PROFILE"
  else
    for p in "${BAND_PROFILES[@]}"; do
      build_band "$p"
    done
  fi
fi

# ── 手机端 ─────────────────────────────────────────────────────────
if [ "$BUILD_PHONE" -eq 1 ]; then
  echo "== 手机端 APK =="
  cd "$ROOT/phone"
  "$GRADLE" assembleRelease --no-daemon
  cp -f app/build/outputs/apk/release/app-release.apk "$ROOT/dist/bandpad.apk"
fi

echo
echo "== 产物 =="
ls -lh "$ROOT/dist"
