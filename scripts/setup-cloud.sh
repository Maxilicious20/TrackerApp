#!/usr/bin/env bash
set -euo pipefail
cd /workspace/TrackerApp
mkdir -p /workspace/.tools /workspace/.android /workspace/.gradle
if [[ ! -x /workspace/.tools/jdk-17.0.16+8/bin/jlink ]]; then
  curl -fsSL 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.16%2B8/OpenJDK17U-jdk_x64_linux_hotspot_17.0.16_8.tar.gz' -o /workspace/.tools/jdk17.tar.gz
  curl -fsSL 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.16%2B8/OpenJDK17U-jdk_x64_linux_hotspot_17.0.16_8.tar.gz.sha256.txt' -o /workspace/.tools/jdk17.sha256
  python3 - <<'PY'
from pathlib import Path
import hashlib
p = Path('/workspace/.tools')
assert hashlib.sha256((p/'jdk17.tar.gz').read_bytes()).hexdigest() == (p/'jdk17.sha256').read_text().split()[0]
PY
  tar -xzf /workspace/.tools/jdk17.tar.gz -C /workspace/.tools
fi
source scripts/cloud-env.sh
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /workspace/.tools/android-tools.zip
  curl -fsSL https://dl.google.com/android/repository/repository2-1.xml -o /workspace/.tools/android-repository.xml
  python3 - <<'PY'
from pathlib import Path
import hashlib, xml.etree.ElementTree as E
p = Path('/workspace/.tools')
archives = E.parse(p/'android-repository.xml').iter('archive')
expected = next(a.find('complete/checksum').text for a in archives if a.findtext('complete/url') == 'commandlinetools-linux-11076708_latest.zip')
assert hashlib.sha1((p/'android-tools.zip').read_bytes()).hexdigest() == expected
PY
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q /workspace/.tools/android-tools.zip -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
# Java tools do not automatically use the cloud's HTTPS_PROXY. No credentials are copied.
python3 - <<'PY'
import os, urllib.parse
from pathlib import Path
p = urllib.parse.urlparse(os.environ.get('HTTPS_PROXY', ''))
f = Path('/workspace/.gradle/gradle.properties')
if p.hostname and not f.exists():
    f.write_text(f'systemProp.http.proxyHost={p.hostname}\nsystemProp.http.proxyPort={p.port or 80}\nsystemProp.https.proxyHost={p.hostname}\nsystemProp.https.proxyPort={p.port or 80}\nsystemProp.http.nonProxyHosts=localhost|127.*\n')
PY
sdk_args=(--sdk_root="$ANDROID_HOME")
if [[ -n "${HTTPS_PROXY:-}" ]]; then
  proxy_host=$(python3 -c 'import os,urllib.parse; print(urllib.parse.urlparse(os.environ["HTTPS_PROXY"]).hostname)')
  proxy_port=$(python3 -c 'import os,urllib.parse; print(urllib.parse.urlparse(os.environ["HTTPS_PROXY"]).port or 80)')
  sdk_args+=(--proxy=http --proxy_host="$proxy_host" --proxy_port="$proxy_port")
fi
# Keep the sdkmanager exit code while its affirmative-input producer may get SIGPIPE.
set +o pipefail
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "${sdk_args[@]}" --licenses > /workspace/.tools/licenses.log
set -o pipefail
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "${sdk_args[@]}" 'platforms;android-35' 'build-tools;34.0.0' 'platform-tools'
if [[ ! -f local.properties ]]; then printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties; fi
./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain
