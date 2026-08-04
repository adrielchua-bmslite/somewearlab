#!/usr/bin/env bash

set -euo pipefail

fail() {
  echo "error: $*" >&2
  exit 1
}

resolve_android_tool() {
  local tool_name="$1"
  local candidate
  local sdk_root

  if command -v "$tool_name" >/dev/null 2>&1; then
    command -v "$tool_name"
    return
  fi

  for sdk_root in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}"; do
    [[ -n "$sdk_root" ]] || continue
    if [[ "$tool_name" == "adb" && -x "$sdk_root/platform-tools/adb" ]]; then
      echo "$sdk_root/platform-tools/adb"
      return
    fi
    candidate="$(find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name "$tool_name" 2>/dev/null | sort | tail -1)"
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      echo "$candidate"
      return
    fi
  done

  fail "$tool_name was not found; set ANDROID_SDK_ROOT or ANDROID_HOME"
}

sha256_file() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | awk '{print $1}'
  else
    fail "sha256sum or shasum is required"
  fi
}

gateway_apk_names=(
  "com.somewearlabs.swtak.plugin.apk"
  "config.arm64_v8a.apk"
  "config.en.apk"
  "config.fr.apk"
  "config.mdpi.apk"
)
