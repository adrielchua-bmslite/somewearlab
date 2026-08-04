#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/common.sh"

if [[ $# -ne 4 ]]; then
  echo "usage: $0 INPUT_APK_DIR OUTPUT_APK_DIR KEYSTORE KEY_ALIAS" >&2
  exit 2
fi

input_dir="$1"
output_dir="$2"
keystore="$3"
key_alias="$4"

[[ -f "$keystore" ]] || fail "keystore does not exist: $keystore"
[[ -n "${GATEWAY_KEYSTORE_PASSWORD:-}" ]] || fail "set GATEWAY_KEYSTORE_PASSWORD"
export GATEWAY_KEY_PASSWORD="${GATEWAY_KEY_PASSWORD:-$GATEWAY_KEYSTORE_PASSWORD}"

zipalign="$(resolve_android_tool zipalign)"
apksigner="$(resolve_android_tool apksigner)"

mkdir -p "$output_dir"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

expected_signer=""
for apk_name in "${gateway_apk_names[@]}"; do
  input_apk="$input_dir/$apk_name"
  aligned_apk="$temporary_dir/$apk_name"
  output_apk="$output_dir/$apk_name"

  [[ -f "$input_apk" ]] || fail "gateway split is missing: $input_apk"
  [[ ! -e "$output_apk" ]] || fail "refusing to overwrite: $output_apk"

  "$zipalign" -p -f 4 "$input_apk" "$aligned_apk"
  "$apksigner" sign \
    --ks "$keystore" \
    --ks-key-alias "$key_alias" \
    --ks-pass env:GATEWAY_KEYSTORE_PASSWORD \
    --key-pass env:GATEWAY_KEY_PASSWORD \
    --out "$output_apk" \
    "$aligned_apk"
  "$apksigner" verify "$output_apk" >/dev/null

  signer="$($apksigner verify --print-certs "$output_apk" 2>/dev/null | awk -F ': ' '/SHA-256 digest/ {print $2; exit}')"
  [[ -n "$signer" ]] || fail "could not read signer from $output_apk"
  if [[ -z "$expected_signer" ]]; then
    expected_signer="$signer"
  elif [[ "$signer" != "$expected_signer" ]]; then
    fail "re-signed split signer mismatch: $apk_name"
  fi
  echo "signed $apk_name"
done

echo "signer_sha256=$expected_signer"
echo "output=$output_dir"
echo "Re-sign SC3 with this same keystore and alias before installation."
