#!/bin/sh
# Re-vendor the APK Signature Scheme v2 golden vectors used by the apksig
# offline test suite (src/apksig.zig "v2 golden vectors").
#
# Source: GrapheneOS platform_tools_apksig, branch 17
# (AOSP apksig test resources):
#   https://github.com/GrapheneOS/platform_tools_apksig/tree/17/src/test/resources/com/android/apksig
#
# One-time network use: run this script to (re)populate testdata/apksig/.
# The Zig test suite itself MUST stay offline and only reads the vendored
# files via @embedFile.
set -eu
BASE="https://raw.githubusercontent.com/GrapheneOS/platform_tools_apksig/17/src/test/resources/com/android/apksig"
OUT="$(dirname "$0")/../testdata/apksig"
mkdir -p "$OUT"
while read -r f; do
    [ -z "$f" ] && continue
    echo "fetch $f"
    curl -sfL -o "$OUT/$f" "$BASE/$f"
done <<'FILES'
v2-only-empty.apk
v2-only-max-sized-eocd-comment.apk
v2-only-unknown-pair-in-apk-sig-block.apk
v2-only-with-ignorable-unsupported-sig-algs.apk
v2-only-with-rsa-pkcs1-sha256-1024.apk
v2-only-with-rsa-pkcs1-sha256-2048.apk
v2-only-with-rsa-pkcs1-sha512-2048.apk
v2-only-with-rsa-pss-sha256-2048.apk
v2-only-with-rsa-pss-sha512-2048.apk
v2-only-with-ecdsa-sha256-p256.apk
v2-only-with-ecdsa-sha256-p384.apk
v2-only-with-ecdsa-sha512-p256.apk
v2-only-with-ecdsa-sha512-p384.apk
v2-only-with-dsa-sha256-1024.apk
v2-only-with-dsa-sha256-2048.apk
v2-only-with-dsa-sha256-3072.apk
v2-only-with-rsa-pkcs1-sha256-2048-sig-does-not-verify.apk
v2-only-with-rsa-pss-sha256-2048-sig-does-not-verify.apk
v2-only-with-ecdsa-sha256-p256-sig-does-not-verify.apk
v2-only-with-dsa-sha256-1024-sig-does-not-verify.apk
v2-only-with-ecdsa-sha256-p256-digest-mismatch.apk
v2-only-with-rsa-pkcs1-sha512-4096-digest-mismatch.apk
v2-only-cert-and-public-key-mismatch.apk
v2-only-no-certs-in-sig.apk
v2-only-signatures-and-digests-block-mismatch.apk
v2-only-apk-sig-block-size-mismatch.apk
v2-only-wrong-apk-sig-block-magic.apk
v2-only-with-rsa-pkcs1-sha256-1024-cert-not-der.apk
v2-only-two-signers.apk
v2-stripped.apk
v2-only-with-rsa-pkcs1-sha256-8192.apk
v2-only-with-rsa-pkcs1-sha512-8192.apk
v2-only-with-rsa-pss-sha256-8192.apk
v2-only-with-ecdsa-sha256-p521.apk
v2-only-with-ecdsa-sha512-p521.apk
FILES
echo "done: $(ls "$OUT"/*.apk | wc -l) files in $OUT"
