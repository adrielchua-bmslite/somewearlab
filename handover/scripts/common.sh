#!/usr/bin/env bash

set -euo pipefail

fail() {
  echo "error: $*" >&2
  exit 1
}

android_sdk_roots() {
  local user_home_dir="${HOME:-}"
  local windows_local_app_data="${LOCALAPPDATA:-}"

  [[ -n "${ANDROID_SDK_ROOT:-}" ]] && echo "$ANDROID_SDK_ROOT"
  [[ -n "${ANDROID_HOME:-}" ]] && echo "$ANDROID_HOME"

  if [[ -n "$user_home_dir" ]]; then
    echo "$user_home_dir/Library/Android/sdk"
    echo "$user_home_dir/Android/Sdk"
    echo "$user_home_dir/AppData/Local/Android/Sdk"
  fi

  if [[ -n "$windows_local_app_data" ]]; then
    echo "$windows_local_app_data/Android/Sdk"
    if command -v cygpath >/dev/null 2>&1; then
      echo "$(cygpath -u "$windows_local_app_data")/Android/Sdk"
    fi
  fi

  echo "/opt/android-sdk"
  echo "/opt/android-sdk-linux"
  echo "/usr/local/lib/android/sdk"
}

android_tool_error() {
  local tool_name="$1"
  cat >&2 <<EOF
error: $tool_name was not found.

Install these components in Android Studio:
  Tools > SDK Manager > SDK Tools
  - Android SDK Build-Tools (provides apksigner, aapt2, and zipalign)
  - Android SDK Platform-Tools (provides adb)

The scripts search PATH, ANDROID_SDK_ROOT, ANDROID_HOME, and the standard
Android Studio SDK locations on macOS, Linux, and Windows Git Bash.

If the SDK is installed in a custom location, run:
  export ANDROID_SDK_ROOT='/absolute/path/to/Android/sdk'

Then retry the command.
EOF
  exit 1
}

resolve_android_tool() {
  local tool_name="$1"
  local candidate
  local sdk_root
  local tool_suffix

  if command -v "$tool_name" >/dev/null 2>&1; then
    command -v "$tool_name"
    return
  fi

  while IFS= read -r sdk_root; do
    [[ -d "$sdk_root" ]] || continue
    for tool_suffix in "" ".exe" ".bat"; do
      if [[ "$tool_name" == "adb" ]]; then
        candidate="$sdk_root/platform-tools/${tool_name}${tool_suffix}"
        if [[ -f "$candidate" && ( -x "$candidate" || "$tool_suffix" == ".bat" ) ]]; then
          echo "$candidate"
          return
        fi
        continue
      fi

      [[ -d "$sdk_root/build-tools" ]] || continue
      candidate="$(find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name "${tool_name}${tool_suffix}" 2>/dev/null | sort | tail -1)"
      if [[ -n "$candidate" && ( -x "$candidate" || "$tool_suffix" == ".bat" ) ]]; then
        echo "$candidate"
        return
      fi
    done
  done < <(android_sdk_roots)

  android_tool_error "$tool_name"
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
