#!/bin/sh
# Bootstrap Gradle Wrapper for AndroidIDE; download and verify the official 8.9 JAR.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_DIR="$APP_HOME/gradle/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/gradle-wrapper.jar"
EXPECTED_SHA256="498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17"
if [ ! -f "$WRAPPER_JAR" ]; then
    mkdir -p "$WRAPPER_DIR"
    TMP_JAR="$WRAPPER_JAR.tmp.$$"
    trap 'rm -f "$TMP_JAR"' EXIT HUP INT TERM
    URL="https://services.gradle.org/distributions/gradle-8.9-wrapper.jar"
    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --retry 2 --silent --show-error "$URL" -o "$TMP_JAR"
    elif command -v wget >/dev/null 2>&1; then
        wget -q "$URL" -O "$TMP_JAR"
    else
        echo "Missing curl/wget: install curl using 'pkg install curl'" >&2
        exit 1
    fi
    ACTUAL_SHA256=$(sha256sum "$TMP_JAR" | cut -d ' ' -f 1)
    if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
        echo "Gradle wrapper JAR SHA-256 mismatch. Refusing to run." >&2
        exit 1
    fi
    mv "$TMP_JAR" "$WRAPPER_JAR"
    trap - EXIT HUP INT TERM
fi
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi
exec "$JAVA" -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
