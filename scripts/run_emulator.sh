#!/usr/bin/env bash
set -euo pipefail
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}}"
if [[ $# -lt 1 ]]; then
  echo "Usage: $0 AVD_NAME [emulator options...]" >&2
  "$sdk_dir/emulator/emulator" -list-avds
  exit 2
fi
avd_name="$1"
shift
# On this Linux host, headless auto selected crashing SwiftShader; host uses the working AMD driver.
exec "$sdk_dir/emulator/emulator" -avd "$avd_name" -gpu "${ROADWATCH_EMULATOR_GPU:-host}" "$@"
