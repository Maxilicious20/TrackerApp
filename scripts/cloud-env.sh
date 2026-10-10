#!/usr/bin/env bash
# Source this only in the prepared Codex cloud machine; Android Studio does not need it.
export JAVA_HOME=/workspace/.tools/jdk-17.0.16+8
export ANDROID_HOME=/workspace/.tools/android-sdk
export ANDROID_USER_HOME=/workspace/.android
export GRADLE_USER_HOME=/workspace/.gradle
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
# Use the host's maintained trust store, including its authorized egress-proxy CA.
if [[ -r /etc/ssl/certs/java/cacerts && "${JAVA_TOOL_OPTIONS:-}" != *javax.net.ssl.trustStore* ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
