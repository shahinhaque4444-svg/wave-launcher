#!/data/data/com.termux/files/usr/bin/bash
# Wave Launcher build: aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner.
# No Gradle, no Android SDK, no network. Every input is on the device.
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
ANDROID_JAR="${ANDROID_JAR:-$PREFIX/share/java/android.jar}"
# aapt2 needs the real framework resource table; the stub android.jar has none.
FRAMEWORK_APK="${FRAMEWORK_APK:-$HOME/.local/share/apktool/framework/1.apk}"
OUT="$ROOT/build"
KEYSTORE="$ROOT/wave.jks"
ALIAS="wave"
PASS="wave-launcher"
MIN_SDK=26
TARGET_SDK=34

[ -f "$ANDROID_JAR" ] || { echo "missing android.jar: $ANDROID_JAR"; exit 1; }
[ -f "$FRAMEWORK_APK" ] || {
  echo "missing framework resources: $FRAMEWORK_APK"
  echo "run: apktool if <a framework-res.apk from this device>"
  exit 1
}

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/6] aapt2 compile"
aapt2 compile --dir "$ROOT/res" -o "$OUT/res.zip"

echo "[2/6] aapt2 link"
aapt2 link \
  -o "$OUT/base.apk" \
  -I "$FRAMEWORK_APK" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  --version-code 1 \
  --version-name 1.0 \
  "$OUT/res.zip"

echo "[3/6] javac"
find "$ROOT/src" -name '*.java' > "$OUT/sources.txt"
find "$OUT/gen" -name '*.java' >> "$OUT/sources.txt"
javac -source 8 -target 8 -nowarn \
  -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/classes" \
  @"$OUT/sources.txt" 2>&1 | grep -v 'bootstrap class path' || true

echo "[4/6] d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
d8 --release --min-api "$MIN_SDK" --lib "$ANDROID_JAR" \
   --output "$OUT/dex" @"$OUT/classes.txt"

echo "[5/6] package"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex )
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[6/6] sign"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias "$ALIAS" \
    -storepass "$PASS" -keypass "$PASS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Wave, OU=Bench, O=Ollie, C=BD" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
  --min-sdk-version "$MIN_SDK" \
  --out "$ROOT/WaveLauncher-1.0.apk" "$OUT/aligned.apk"

apksigner verify --min-sdk-version "$MIN_SDK" "$ROOT/WaveLauncher-1.0.apk" && echo "signed OK"
echo "apk: $ROOT/WaveLauncher-1.0.apk"
