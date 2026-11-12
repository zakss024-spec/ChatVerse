#!/usr/bin/env bash
# Run as one script file so compound shell blocks are not split by the emulator action.
set +e

# Retry transient ADB disconnects while Android framework services settle.
adb_retry() {
  local tries=0
  until "$@"; do
    tries=$((tries + 1))
    if [ "$tries" -ge 8 ]; then
      echo "ADB command failed after $tries attempts: $*"
      return 1
    fi
    echo "ADB temporarily unavailable (attempt $tries); reconnecting..."
    adb reconnect offline >/dev/null 2>&1 || true
    sleep 5
  done
}

# Always preserve app/system logs, including when an adb command fails midway.
capture_diagnostics() {
  adb_retry adb logcat -b all -d -v threadtime > emulator-logcat.txt 2>&1 || true
}
trap capture_diagnostics EXIT

adb_retry adb wait-for-device
booted=""
for attempt in $(seq 1 300); do
  booted="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\\r' || true)"
  [ "$booted" = "1" ] && break
  # A device can reconnect while system_server is starting; tolerate that.
  if [ $((attempt % 15)) -eq 0 ]; then
    adb reconnect offline >/dev/null 2>&1 || true
  fi
  sleep 2
done
if [ "$booted" != "1" ]; then
  echo "Android emulator framework did not finish booting within 10 minutes."
  capture_diagnostics
  exit 1
fi

adb_retry adb install -r app/build/outputs/apk/debug/app-debug.apk
install_status=$?
if [ "$install_status" -ne 0 ]; then
  echo "APK installation failed with exit code $install_status"
  capture_diagnostics
  exit "$install_status"
fi

adb_retry adb shell am force-stop com.chatverse.app
adb_retry adb shell am start -W -n com.chatverse.app/.MainActivity
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
adb_retry adb exec-out screencap -p > chatverse-preview.png
capture_status=$?
if [ "$capture_status" -ne 0 ] || [ ! -s chatverse-preview.png ]; then
  echo "Could not capture emulator screenshot through adb exec-out."
  exit 1
fi
