#!/usr/bin/env bash
# Run as one script file so compound shell blocks are not split by the emulator action.
set +e

adb install -r app/build/outputs/apk/debug/app-debug.apk
install_status=$?
if [ "$install_status" -ne 0 ]; then
  echo "APK installation failed with exit code $install_status"
  adb logcat -d -t 1200 > emulator-logcat.txt
  exit "$install_status"
fi

adb shell am force-stop com.chatverse.app
adb shell am start -W -n com.chatverse.app/.MainActivity
start_status=$?
if [ "$start_status" -ne 0 ]; then
  echo "Activity launch command failed with exit code $start_status"
  adb logcat -d -t 2000 > emulator-logcat.txt
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

adb logcat -d -t 3000 > emulator-logcat.txt
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
adb shell screencap -p /sdcard/chatverse-preview.png
capture_status=$?
if [ "$capture_status" -ne 0 ]; then
  echo "Could not capture emulator screenshot."
  exit "$capture_status"
fi
adb pull /sdcard/chatverse-preview.png chatverse-preview.png
pull_status=$?
if [ "$pull_status" -ne 0 ]; then
  echo "Could not retrieve emulator screenshot."
  exit "$pull_status"
fi
