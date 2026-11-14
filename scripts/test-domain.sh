#!/usr/bin/env bash
# Compiles the pure-Kotlin domain engine and runs its test suite. No Android SDK needed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build"
rm -rf "$OUT" && mkdir -p "$OUT/main" "$OUT/test"
"$ROOT/scripts/kotlinc.sh" -jvm-target 17 -d "$OUT/main" $(find "$ROOT/src/main" -name "*.kt")
KOTLINC_CLASSPATH="$OUT/main" "$ROOT/scripts/kotlinc.sh" -jvm-target 17 -d "$OUT/test" $(find "$ROOT/src/test" -name "*.kt")
STDLIB="${KOTLIN_HOME:+$KOTLIN_HOME/lib/kotlin-stdlib.jar}"
STDLIB="${STDLIB:-/opt/gradle/lib/kotlin-stdlib-2.0.21.jar}"
java -cp "$OUT/test:$OUT/main:$STDLIB" com.school.timetable.tests.TestMainKt
