#!/bin/sh
# 手工打包（不依赖 Gradle）。本机（aarch64）已经在 /tmp/abuild 里准备了工具链；
# 其他机器可以用 ANDROID_JAR / AAPT2 / D8_JAR / APKSIGNER_JAR / KEYSTORE 覆盖。
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUT=${OUT:-$ROOT/dist}
VERSION_CODE=${VERSION_CODE:-2}
VERSION_NAME=${VERSION_NAME:-2.0}
JDK=${JDK:-/tmp/abuild/jdk}
ANDROID_JAR=${ANDROID_JAR:-/tmp/abuild/platform/android-34/android.jar}
AAPT2=${AAPT2:-/tmp/termux/prefix/data/data/com.termux/files/usr/bin/aapt2}
AAPT2_LIBS=${AAPT2_LIBS:-/tmp/termux/prefix/data/data/com.termux/files/usr/lib}
D8_JAR=${D8_JAR:-/tmp/abuild/bt/android-14/lib/d8.jar}
APKSIGNER_JAR=${APKSIGNER_JAR:-/tmp/abuild/bt/android-14/lib/apksigner.jar}
KEYSTORE=${KEYSTORE:-$ROOT/keys/debug.keystore}
if [ ! -e "$KEYSTORE" ]; then KEYSTORE=/tmp/abuild/keys/debug.keystore; fi
KS_PASS=${KS_PASS:-android}
BUILD=${BUILD:-/tmp/jiacang-build}

for f in "$JDK/bin/javac" "$ANDROID_JAR" "$AAPT2" "$D8_JAR" "$APKSIGNER_JAR" "$KEYSTORE"; do
  if [ ! -e "$f" ]; then
    echo "missing: $f (set the matching env var)" >&2
    exit 1
  fi
done

rm -rf "$BUILD"
mkdir -p "$BUILD/res" "$BUILD/classes" "$BUILD/dex" "$BUILD/gen" "$OUT"
PATH="$JDK/bin:$PATH"
export PATH

# aapt2 是 aarch64 Termux 版，需要它自带的 libc++/protobuf 等；
# 只给 aapt2 临时设置 LD_LIBRARY_PATH，别污染后面的 python3 / java。
run_aapt2() {
  if [ -d "$AAPT2_LIBS" ]; then
    LD_LIBRARY_PATH="$AAPT2_LIBS" "$AAPT2" "$@"
  else
    "$AAPT2" "$@"
  fi
}
run_aapt2 compile --dir "$ROOT/app/src/main/res" -o "$BUILD/res.zip"
# 手工 link 需要 package 属性；AGP/Gradle 构建时由 namespace 提供，不需要它
sed 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android">#<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.local.globalclip">#' \
  "$ROOT/app/src/main/AndroidManifest.xml" > "$BUILD/AndroidManifest.xml"
run_aapt2 link --manifest "$BUILD/AndroidManifest.xml" -I "$ANDROID_JAR" \
  -R "$BUILD/res.zip" --java "$BUILD/gen" -o "$BUILD/base.apk" \
  --min-sdk-version 29 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" --auto-add-overlay

javac -source 17 -target 17 -classpath "$ANDROID_JAR" -d "$BUILD/classes" \
  $(find "$ROOT/app/src/main/java" "$BUILD/gen" -name '*.java' | sort)
jar cf "$BUILD/classes.jar" -C "$BUILD/classes" .
java -cp "$D8_JAR" com.android.tools.r8.D8 --release --min-api 29 \
  --lib "$ANDROID_JAR" --output "$BUILD/dex" "$BUILD/classes.jar"

python3 - "$BUILD/base.apk" "$BUILD/unsigned.apk" "$BUILD/dex/classes.dex" <<'PY'
import shutil, sys, zipfile
shutil.copy(sys.argv[1], sys.argv[2])
with zipfile.ZipFile(sys.argv[2], 'a') as z:
    z.write(sys.argv[3], 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
PY
python3 "$ROOT/scripts/align_apk.py" "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

java -jar "$APKSIGNER_JAR" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KS_PASS" --v1-signing-enabled false --v2-signing-enabled true \
  --v3-signing-enabled true --v4-signing-enabled false \
  --out "$OUT/jiacang-$VERSION_NAME.apk" "$BUILD/aligned.apk"
java -jar "$APKSIGNER_JAR" verify --print-certs "$OUT/jiacang-$VERSION_NAME.apk"
echo "built: $OUT/jiacang-$VERSION_NAME.apk"
