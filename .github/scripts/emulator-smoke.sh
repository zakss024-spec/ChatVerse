#!/usr/bin/env bash
# Run as one script file so compound shell blocks are not split by the emulator action.
set +e

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

capture_diagnostics() {
  adb_retry adb logcat -b all -d -v threadtime > emulator-logcat.txt 2>&1 || true
}
trap capture_diagnostics EXIT

adb_retry adb wait-for-device
booted=""
for attempt in $(seq 1 300); do
  booted="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [ "$booted" = "1" ] && break
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

wait_for_system_services() {
  local stable=0
  for attempt in $(seq 1 60); do
    local package_state activity_state
    package_state="$(adb shell service check package 2>/dev/null | tr -d '\r' || true)"
    activity_state="$(adb shell service check activity 2>/dev/null | tr -d '\r' || true)"
    if [[ "$package_state" == *"found"* && "$activity_state" == *"found"* ]]; then
      stable=$((stable + 1))
      if [ "$stable" -ge 3 ]; then
        echo "Android PackageManager and ActivityManager are stable."
        return 0
      fi
    else
      stable=0
    fi
    echo "Waiting for Android system services (attempt $attempt/60)..."
    sleep 5
  done
  echo "Android system services did not become stable."
  capture_diagnostics
  return 1
}

wait_for_system_services || exit 1

install_status=1
for install_attempt in $(seq 1 3); do
  if adb install -r app/build/outputs/apk/debug/app-debug.apk; then
    install_status=0
    break
  fi
  echo "APK install attempt $install_attempt failed; waiting for system services."
  wait_for_system_services || true
done
if [ "$install_status" -ne 0 ]; then
  echo "APK installation failed after 3 attempts."
  capture_diagnostics
  exit 1
fi

if ! adb shell pm path com.chatverse.app | grep -q "package:"; then
  echo "PackageManager cannot confirm the installed ChatVerse package."
  capture_diagnostics
  exit 1
fi

adb_retry adb shell am force-stop com.chatverse.app || exit 1
launch_output="$(adb shell am start -W -n com.chatverse.app/.MainActivity 2>&1)"
launch_status=$?
printf '%s\n' "$launch_output"

# On heavily loaded hosted emulators, am start -W may time out even though
# ActivityTaskManager subsequently resumes the requested activity. Verify the
# actual foreground state below instead of treating the wait timeout alone as
# an app failure.
if [ "$launch_status" -ne 0 ] && ! printf '%s\n' "$launch_output" | grep -qE '^Status: timeout
app_pid="$(adb shell pidof com.chatverse.app 2>/dev/null | tr -d '\r' || true)"
if [ -z "$app_pid" ]; then
  echo "ChatVerse process was not observed after foreground launch."
  capture_diagnostics
  exit 1
fi

sleep 5
capture_diagnostics
if grep -E "Process: com\\.chatverse\\.app|ANR in com\\.chatverse\\.app" emulator-logcat.txt; then
  echo "App-specific fatal error or ANR detected."
  exit 1
fi

adb_retry adb exec-out screencap -p > chatverse-preview.png
capture_status=$?
if [ "$capture_status" -ne 0 ] || [ ! -s chatverse-preview.png ]; then
  echo "Could not capture emulator screenshot through adb exec-out."
  exit 1
fi
; then
  echo "ChatVerse activity launch command failed (exit=$launch_status)."
  capture_diagnostics
  exit 1
fi

# A process existing is not enough: confirm Android actually resumed ChatVerse.
resumed_activity=""
for attempt in $(seq 1 45); do
  resumed_activity="$(adb shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity' | head -n 2 || true)"
  if printf '%s\n' "$resumed_activity" | grep -q 'com\.chatverse\.app'; then
    break
  fi
  sleep 2
done
if ! printf '%s\n' "$resumed_activity" | grep -q 'com\.chatverse\.app'; then
  echo "ChatVerse did not become the resumed foreground activity."
  echo "Last resumed activity state: $resumed_activity"
  capture_diagnostics
  exit 1
fi

app_pid="$(adb shell pidof com.chatverse.app 2>/dev/null | tr -d '\r' || true)"
if [ -z "$app_pid" ]; then
  echo "ChatVerse process was not observed after foreground launch."
  capture_diagnostics
  exit 1
fi

sleep 5
capture_diagnostics
if grep -E "Process: com\\.chatverse\\.app|ANR in com\\.chatverse\\.app" emulator-logcat.txt; then
  echo "App-specific fatal error or ANR detected."
  exit 1
fi

adb_retry adb exec-out screencap -p > chatverse-preview.png
capture_status=$?
if [ "$capture_status" -ne 0 ] || [ ! -s chatverse-preview.png ]; then
  echo "Could not capture emulator screenshot through adb exec-out."
  exit 1
fi
