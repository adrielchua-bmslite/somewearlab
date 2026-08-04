#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/common.sh"

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "usage: $0 APK_DIR [DEVICE_SERIAL]" >&2
  exit 2
fi

apk_dir="$1"
device_serial="${2:-}"
adb="$(resolve_android_tool adb)"
adb_args=()
if [[ -n "$device_serial" ]]; then
  adb_args=(-s "$device_serial")
fi

apk_paths=()
for apk_name in "${gateway_apk_names[@]}"; do
  apk="$apk_dir/$apk_name"
  [[ -f "$apk" ]] || fail "gateway split is missing: $apk"
  apk_paths+=("$apk")
done

device_count="$($adb devices | awk 'NR > 1 && $2 == "device" {count++} END {print count+0}')"
if [[ -z "$device_serial" && "$device_count" -ne 1 ]]; then
  fail "connect exactly one Android device or pass DEVICE_SERIAL"
fi

echo "Installing the complete gateway split set. Existing packages are not uninstalled automatically."
"$adb" "${adb_args[@]}" install-multiple -r "${apk_paths[@]}"

permissions=(
  android.permission.BLUETOOTH_SCAN
  android.permission.BLUETOOTH_CONNECT
  android.permission.BLUETOOTH_ADVERTISE
  android.permission.ACCESS_FINE_LOCATION
)

for permission in "${permissions[@]}"; do
  if ! "$adb" "${adb_args[@]}" shell pm grant com.somewearlabs.swtak.plugin "$permission"; then
    echo "warning: Android did not grant $permission; grant it in system settings if required" >&2
  fi
done

package_dump="$($adb "${adb_args[@]}" shell dumpsys package com.somewearlabs.swtak.plugin)"
grep -q 'com.somewearlabs.swtak.plugin' <<<"$package_dump" || fail "gateway package was not found after installation"

echo "package=com.somewearlabs.swtak.plugin"
echo "installation=OK"
echo "Now install SC3 signed with the same certificate and call somewear.info()."
