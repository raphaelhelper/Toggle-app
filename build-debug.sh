#!/bin/sh
set -eu

gradle assembleDebug
printf '\nAPK: %s\n' "$(pwd)/build/outputs/apk/debug/ToggleMeaning_Test-debug.apk"
