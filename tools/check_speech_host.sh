#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -ne 2 ]; then
  echo 'Usage: bash tools/check_speech_host.sh model.bin uk-natural.wav' >&2
  exit 2
fi
speech_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
speech_build="$speech_root/build/speech-host"
speech_cmake="${HOLOS_CMAKE:-cmake}"
"$speech_cmake" -S "$speech_root/tools/speech-host" -B "$speech_build" -DCMAKE_BUILD_TYPE=Release
"$speech_cmake" --build "$speech_build" --parallel 2
mkdir -p "$speech_build/classes"
"$JAVA_HOME/bin/javac" -d "$speech_build/classes" \
  "$speech_root/app/src/main/java/ua/holoscalendar/redmi/WhisperEngine.java" \
  "$speech_root/app/src/main/java/ua/holoscalendar/redmi/SpeechModel.java" \
  "$speech_root/tools/speech-host/HostWhisperSmoke.java"
"$JAVA_HOME/bin/java" -Djava.library.path="$speech_build" -cp "$speech_build/classes" \
  ua.holoscalendar.redmi.HostWhisperSmoke "$1" "$2"
