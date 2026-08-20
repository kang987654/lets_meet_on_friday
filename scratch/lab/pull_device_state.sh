#!/usr/bin/env bash
# 실기기 상태를 fixtures/ 로 한 번에 회수한다.
#
# [WHY] 2026-08-12 세션에서는 이걸 대화형으로 하다가 중간에 USB 가 빠져 녹음 파일을 놓쳤다.
# 한 번에 끝나는 스크립트로 만들어 두면 다음에는 연결 시간이 짧아도 된다.
#
# 사용법:  bash pull_device_state.sh
set -u

# Git Bash 가 /sdcard 를 Windows 경로로 바꾸는 것을 막는다.
export MSYS_NO_PATHCONV=1

ADB="${ADB:-/c/Users/J/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
PKG="com.kosmos.app"
OUT="$(cd "$(dirname "$0")" && pwd)/fixtures"

mkdir -p "$OUT"

if ! "$ADB" devices | grep -qw device; then
  echo "[!] 기기가 연결되지 않았다. USB 디버깅 허용까지 확인할 것."
  exit 1
fi

echo "== DB (대화·감사·일정) =="
# [WHY] -wal 과 -shm 도 함께 가져온다. 최근 쓰기가 WAL 에만 있으면 본 파일만으로는 마지막
# 대화가 빠진다. sqlite 는 같은 디렉터리에 같은 이름으로 두면 알아서 재생한다.
for f in kosmos_db kosmos_db-wal kosmos_db-shm; do
  if "$ADB" exec-out run-as "$PKG" cat "databases/$f" > "$OUT/$f.tmp" 2>/dev/null \
     && [ -s "$OUT/$f.tmp" ]; then
    mv "$OUT/$f.tmp" "$OUT/$f"
    echo "  $f  $(wc -c < "$OUT/$f") bytes"
  else
    rm -f "$OUT/$f.tmp"
    echo "  $f  (없음 — WAL 은 체크포인트됐을 수 있다)"
  fi
done

echo "== 마지막 녹음 =="
# [WHY] 앱은 전사 후 이 파일을 지운다. 실패했을 때만 남으므로 없을 수도 있다.
if "$ADB" exec-out run-as "$PKG" cat "cache/kosmos_audio_input.wav" > "$OUT/voice_input.wav.tmp" 2>/dev/null \
   && [ -s "$OUT/voice_input.wav.tmp" ]; then
  mv "$OUT/voice_input.wav.tmp" "$OUT/voice_input.wav"
  bytes=$(wc -c < "$OUT/voice_input.wav")
  echo "  voice_input.wav  $bytes bytes (~$((  (bytes - 44) / 32000 ))s)"
else
  rm -f "$OUT/voice_input.wav.tmp"
  echo "  없음 — 전사가 성공했으면 지워진 게 정상이다. TTS 합성본으로 대체된다."
fi

echo "== logcat =="
"$ADB" logcat -d > "$OUT/device_run.log" 2>&1 || true
echo "  device_run.log  $(wc -l < "$OUT/device_run.log") lines"

echo
echo "회수 완료: $OUT"
echo "확인:  ../.venv/Scripts/python.exe device_fixture.py"
