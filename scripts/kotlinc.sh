#!/usr/bin/env bash
# Runs the Kotlin compiler from a kotlinc distribution (KOTLIN_HOME) or, failing that,
# from the kotlin-compiler-embeddable jars that ship with Gradle (GRADLE_HOME).
set -euo pipefail

if [[ -n "${KOTLIN_HOME:-}" && -f "$KOTLIN_HOME/lib/kotlin-compiler.jar" ]]; then
  CP="$KOTLIN_HOME/lib/kotlin-compiler.jar:$KOTLIN_HOME/lib/kotlin-stdlib.jar:$KOTLIN_HOME/lib/kotlin-script-runtime.jar:$KOTLIN_HOME/lib/kotlin-reflect.jar:$KOTLIN_HOME/lib/kotlin-daemon.jar:$KOTLIN_HOME/lib/trove4j.jar:$KOTLIN_HOME/lib/annotations-13.0.jar"
  STDLIB="$KOTLIN_HOME/lib/kotlin-stdlib.jar"
else
  GRADLE_HOME="${GRADLE_HOME:-/opt/gradle}"
  LIB="$GRADLE_HOME/lib"
  CP="$LIB/kotlin-compiler-embeddable-2.0.21.jar:$LIB/kotlin-stdlib-2.0.21.jar:$LIB/kotlin-script-runtime-2.0.21.jar:$LIB/kotlin-reflect-2.0.21.jar:$LIB/kotlin-daemon-embeddable-2.0.21.jar:$LIB/trove4j-1.0.20200330.jar:$LIB/annotations-24.0.1.jar:$LIB/kotlinx-coroutines-core-jvm-1.6.4.jar:$LIB/kotlin-scripting-compiler-embeddable-2.0.21.jar:$LIB/kotlin-scripting-compiler-impl-embeddable-2.0.21.jar:$LIB/kotlin-scripting-common-2.0.21.jar:$LIB/kotlin-scripting-jvm-2.0.21.jar"
  STDLIB="$LIB/kotlin-stdlib-2.0.21.jar"
fi

EXTRA="${KOTLINC_CLASSPATH:-}"
exec java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$STDLIB${EXTRA:+:$EXTRA}" "$@"
