#!/usr/bin/env bash
# Run as one script file so compound shell blocks are not split by the emulator action.
set +e

# Always preserve app/system logs, including when an adb command fails midway.
capture_diagnostics() {
  adb logcat -b all -d -v threadtime > emulator-logcat.txt 2>&1 || true
}
trap capture_diagnostics EXIT

adb wait-for-device
for attempt in $(seq 1 90); do
  booted="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\\r' || true)"
  [ "$booted" = "1" ] && break
  sleep 2
done
if [ "$booted" != "1" ]; then
  echo "Android emulator did not finish booting."
  exit 1
fi

adb install -r app/build/outputs/apk/debug/app-debug.apk
install_status=$?
if [ "$install_status" -ne 0 ]; then
  echo "APK installation failed with exit code $install_status"
  capture_diagnostics
  exit "$install_status"
fi

adb shell am force-stop com.chatverse.app
adb shell am start -W -n com.chatverse.app/.MainActivity
start_status=$?
if [ "$start_status" -ne 0 ]; then
  echo "Activity launch command failed with exit code $start_status"
  capture_diagnostics
  exit "$start_status"
fi

app_pid=""
for attempt in $(seq 1 60); do
  app_pid="$(adb shell pidof com.chatverse.app 2>/dev/null | tr -d '\r' || true)"
  if [ -n "$app_pid" ]; then
    break
  fi
  sleep 2
done

capture_diagnostics
if [ -z "$app_pid" ]; then
  echo "ChatVerse process was not observed after launch."
  echo "System ANRs are diagnostic only; inspect app-specific fatal errors below."
  grep -E "FATAL EXCEPTION|ANR in com.chatverse.app|Process: com.chatverse.app" emulator-logcat.txt || true
  exit 1
fi

if grep -E "FATAL EXCEPTION:|ANR in com.chatverse.app|Process: com.chatverse.app" emulator-logcat.txt; then
  echo "App-specific fatal error or ANR detected."
  exit 1
fi

sleep 8
adb exec-out screencap -p > chatverse-preview.png
capture_status=$?
if [ "$capture_status" -ne 0 ] || [ ! -s chatverse-preview.png ]; then
  echo "Could not capture emulator screenshot through adb exec-out."
  exit 1
fi
