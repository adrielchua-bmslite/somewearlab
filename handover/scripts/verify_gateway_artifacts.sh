#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
source "$script_dir/common.sh"

apk_dir="${1:-$repo_root/build/signed-splits-v2}"
aar="$repo_root/somewear-gateway-sdk/dist/somewear-gateway-sdk-0.1.0.aar"
apksigner="$(resolve_android_tool apksigner)"
aapt2="$(resolve_android_tool aapt2)"

[[ -f "$aar" ]] || fail "SDK AAR is missing: $aar"

if [[ $# -eq 0 ]]; then
  while read -r expected_hash relative_path; do
    [[ -n "$expected_hash" && -n "$relative_path" ]] || continue
    artifact="$repo_root/$relative_path"
    [[ -f "$artifact" ]] || fail "hashed artifact is missing: $relative_path"
    actual_hash="$(sha256_file "$artifact")"
    [[ "$actual_hash" == "$expected_hash" ]] || fail "SHA-256 mismatch: $relative_path"
  done < "$repo_root/handover/SHA256SUMS"
  echo "committed_hashes=OK"
else
  echo "committed_hashes=SKIPPED (custom APK directory)"
fi

expected_signer=""
for apk_name in "${gateway_apk_names[@]}"; do
  apk="$apk_dir/$apk_name"
  [[ -f "$apk" ]] || fail "gateway split is missing: $apk"
  "$apksigner" verify "$apk" >/dev/null
  signer="$($apksigner verify --print-certs "$apk" 2>/dev/null | awk -F ': ' '/SHA-256 digest/ {print $2; exit}')"
  [[ -n "$signer" ]] || fail "could not read signer from $apk"
  if [[ -z "$expected_signer" ]]; then
    expected_signer="$signer"
  elif [[ "$signer" != "$expected_signer" ]]; then
    fail "gateway split signer mismatch: $apk_name"
  fi
  echo "$(sha256_file "$apk")  $apk_name"
done

base_apk="$apk_dir/com.somewearlabs.swtak.plugin.apk"
manifest="$($aapt2 dump xmltree --file AndroidManifest.xml "$base_apk")"
grep -q 'com.somewearlabs.gateway.SomewearGatewayProvider' <<<"$manifest" || fail "base APK does not declare SomewearGatewayProvider"
grep -q 'com.somewearlabs.swtak.plugin.somewear.gateway' <<<"$manifest" || fail "base APK has the wrong provider authority"
grep -q 'com.somewearlabs.gateway.WorkspaceQrScannerActivity' <<<"$manifest" || fail "base APK does not declare the gateway QR scanner"

echo "$(sha256_file "$aar")  somewear-gateway-sdk-0.1.0.aar"
echo "signer_sha256=$expected_signer"
echo "provider=com.somewearlabs.gateway.SomewearGatewayProvider"
echo "authority=com.somewearlabs.swtak.plugin.somewear.gateway"
echo "verification=OK"
