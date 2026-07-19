#!/bin/bash
set -e

PROJECT="/storage/emulated/0/admin_nativ"
BUILD="$PROJECT/build"
ANDROID_JAR="/data/data/com.termux/files/usr/share/java/android.jar"
FRAMEWORK_RES="/system/framework/framework-res.apk"
OUTPUT_APK="/storage/emulated/0/admin_nativ/TVAdmin.apk"

export ANDROID_DATA=/nonexistent
export ANDROID_ROOT=/nonexistent

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/obj" "$BUILD/dex" "$BUILD/apk" "$BUILD/compiled"

echo ""
echo "========================================="
echo "  TV ADMIN - NATIVE APK BUILD (AAPT2)"
echo "========================================="
echo ""

if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar not found at $ANDROID_JAR"
    exit 1
fi

echo "[1/6] AAPT2 - Compiling resources..."
find "$PROJECT/res" -name "*.xml" -o -name "*.png" -o -name "*.jpg" -o -name "*.jpeg" -o -name "*.gif" | while read f; do
    aapt2 compile -o "$BUILD/compiled" "$f" 2>&1
done
echo "  Resources compiled!"

echo ""
echo "[2/6] AAPT2 - Linking resources..."
COMPILED_FILES=$(find "$BUILD/compiled" -name "*.flat" -type f)
aapt2 link \
    -o "$BUILD/apk/base.apk" \
    -I "$FRAMEWORK_RES" \
    --manifest "$PROJECT/AndroidManifest.xml" \
    --java "$BUILD/gen" \
    --auto-add-overlay \
    $COMPILED_FILES 2>&1
echo "  Resources linked! R.java generated!"

echo ""
echo "[3/6] JAVAC - Compiling Java..."
find "$BUILD/gen" -name "*.java" > "$BUILD/sources.txt"
find "$PROJECT/src" -name "*.java" >> "$BUILD/sources.txt"
echo "  Found $(wc -l < "$BUILD/sources.txt") source files"
javac -source 1.8 -target 1.8 \
    -classpath "$ANDROID_JAR" \
    -d "$BUILD/obj" \
    @"$BUILD/sources.txt" 2>&1
echo "  Java compiled!"

echo ""
echo "[4/6] D8 - Creating DEX..."
d8 --output "$BUILD/dex" $(find "$BUILD/obj" -name "*.class") --lib "$ANDROID_JAR" 2>&1
echo "  classes.dex created!"

echo ""
echo "[5/6] Adding DEX to APK..."
cd "$BUILD/apk"
cp "$BUILD/dex/classes.dex" .
zip -j base.apk classes.dex 2>&1 || aapt add base.apk classes.dex 2>&1
rm -f classes.dex
echo "  DEX added to APK!"

echo ""
echo "[6/6] SIGNING APK..."
KEYSTORE="$PROJECT/release.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias release \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android123 -keypass android123 \
        -dname "CN=TV Admin, OU=Kafi, O=TVByKafi, L=Dhaka, ST=Dhaka, C=BD" 2>&1
fi

apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias release \
    --ks-pass pass:android123 \
    --key-pass pass:android123 \
    --out "$OUTPUT_APK" \
    base.apk 2>&1

echo "  APK signed!"

echo ""
echo "========================================="
echo "  BUILD SUCCESSFUL!"
echo "  APK: $OUTPUT_APK"
SIZE=$(ls -lh "$OUTPUT_APK" | awk '{print $5}')
echo "  Size: $SIZE"
echo "========================================="
echo ""

apksigner verify "$OUTPUT_APK" 2>&1 && echo "  Signature: VERIFIED"
