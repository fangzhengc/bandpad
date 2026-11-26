#!/usr/bin/env bash
# 一键准备两端共用的签名材料。
#
# interconnect 要求 RPK 与 APK「同包名 + 同签名」，所以两端必须用同一次生成的这一套。
# 本脚本做三件事：
#   1. 生成 phone/keystore/pageflip.p12        （安卓 keystore，PKCS12）
#   2. 导出 band/sign/{debug,release}/*.pem    （快应用 RPK 签名）
#   3. 写出 phone/keystore.properties          （Gradle 读取的密码配置）
#
# 用法：
#   bash scripts/gen-pem.sh                            # 密码随机生成并打印
#   BANDPAD_PASS=自定义密码 bash scripts/gen-pem.sh      # 指定密码
#   BANDPAD_PASS=xxx bash scripts/gen-pem.sh --force     # 丢弃旧 keystore 重建
#
# 环境要求：JDK 17（keytool）+ openssl。两者都在 PATH 里即可，或用 JAVA_HOME 指定 JDK。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STORE="$ROOT/phone/keystore/pageflip.p12"
PROPS="$ROOT/phone/keystore.properties"
ALIAS="bandpad"
SIGN_DIRS=("$ROOT/band/sign/debug" "$ROOT/band/sign/release")
FORCE=0
[ "${1:-}" = "--force" ] && FORCE=1

KEYTOOL="keytool"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
  KEYTOOL="$JAVA_HOME/bin/keytool"
fi
command -v "$KEYTOOL" >/dev/null 2>&1 || {
  echo "找不到 keytool，请安装 JDK 17 或设置 JAVA_HOME" >&2
  exit 1
}
command -v openssl >/dev/null 2>&1 || {
  echo "找不到 openssl，请先安装（Windows 可用 Git Bash 自带的）" >&2
  exit 1
}

# ── 密码：环境变量 > 已有配置 > 随机 ────────────────────────────────
PASS="${BANDPAD_PASS:-}"
if [ -z "$PASS" ] && [ -f "$PROPS" ] && [ "$FORCE" -eq 0 ]; then
  PASS="$(grep -E '^store\.password=' "$PROPS" | head -1 | cut -d= -f2- || true)"
fi
GENERATED=0
if [ -z "$PASS" ]; then
  PASS="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 20)"
  GENERATED=1
fi

# ── 1. keystore ────────────────────────────────────────────────────
mkdir -p "$(dirname "$STORE")"
if [ -f "$STORE" ] && [ "$FORCE" -eq 1 ]; then
  mv "$STORE" "$STORE.bak.$(date +%Y%m%d%H%M%S)"
  echo "旧 keystore 已备份"
fi

if [ ! -f "$STORE" ]; then
  echo "生成 keystore…"
  "$KEYTOOL" -genkeypair -noprompt \
    -storetype PKCS12 -keystore "$STORE" -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$PASS" -keypass "$PASS" \
    -dname "CN=BandPad, OU=Personal, O=BandPad, L=Beijing, ST=Beijing, C=CN" >/dev/null
  echo "  已生成 $STORE"
else
  echo "复用已有 keystore：$STORE"
fi

# ── 2. 导出快应用 pem ──────────────────────────────────────────────
# 临时文件放工程内再挪走：Windows 上 mktemp -d 返回的路径带盘符，
# 直接被 rm -rf 会被安全策略拦，所以用 mv 归档而不是删除。
TMP="$ROOT/.tmp_sign"
mkdir -p "$TMP"
ARCHIVE="${TMPDIR:-/tmp}/bandpad_attic"
mkdir -p "$ARCHIVE"
trap 'mv "$TMP" "$ARCHIVE/sign_$$_$(date +%s)" 2>/dev/null || true' EXIT

openssl pkcs12 -in "$STORE" -nodes -passin "pass:$PASS" -out "$TMP/all.pem" 2>/dev/null || {
  echo "导出失败：keystore 密码不对？可设 BANDPAD_PASS=你的密码 再试，或用 --force 重建" >&2
  exit 1
}
awk '/-----BEGIN (PRIVATE KEY|ENCRYPTED PRIVATE KEY)-----/,/-----END (PRIVATE KEY|ENCRYPTED PRIVATE KEY)-----/' \
  "$TMP/all.pem" > "$TMP/private.pem"
awk '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/' "$TMP/all.pem" > "$TMP/certificate.pem"
[ -s "$TMP/private.pem" ] && [ -s "$TMP/certificate.pem" ] || {
  echo "未能从 keystore 提取私钥或证书" >&2
  exit 1
}

for d in "${SIGN_DIRS[@]}"; do
  mkdir -p "$d"
  cp "$TMP/private.pem" "$TMP/certificate.pem" "$d/"
done
echo "  已导出 band/sign/{debug,release}/{private,certificate}.pem"

# ── 3. keystore.properties ─────────────────────────────────────────
cat > "$PROPS" <<EOF
# 由 scripts/gen-pem.sh 生成，请勿提交到版本库
store.file=keystore/pageflip.p12
store.password=$PASS
key.alias=$ALIAS
key.password=$PASS
EOF
chmod 600 "$PROPS" 2>/dev/null || true
echo "  已写入 phone/keystore.properties"

# ── 收尾提示 ───────────────────────────────────────────────────────
echo
if [ "$GENERATED" -eq 1 ]; then
  echo "本次自动生成的密码： $PASS"
  echo "请记下来（改签名会导致已装的 App 与手环不能混装）。"
else
  echo "密码：使用你提供的那个"
fi
echo
echo "下一步：bash scripts/build.sh"
echo "注意：换签名后两端都要卸载重装，手环端还要 bump versionCode 才能覆盖。"
