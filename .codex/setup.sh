#!/usr/bin/env bash
# Codex Environment setup: Ubuntu/Debian x86_64. Run from any directory.
set -Eeuo pipefail
trap 'echo "Setup failed at line $LINENO" >&2' ERR
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
  echo 'This setup requires an Ubuntu/Debian Linux x86_64 environment.' >&2
  exit 1
fi
admin=()
if [[ "$EUID" -ne 0 ]]; then admin=(sudo -n); fi
"${admin[@]}" apt-get update
"${admin[@]}" env DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
  openjdk-17-jdk-headless ca-certificates curl unzip python3

# Stable paths can also be entered into the Environment variables UI.
"${admin[@]}" mkdir -p /opt/homebudget/android-sdk
"${admin[@]}" chown "$(id -u):$(id -g)" /opt/homebudget/android-sdk
jdk_path="$(dirname "$(dirname "$(dpkg -L openjdk-17-jdk-headless | sed -n '\|/bin/javac$|p' | head -n 1)")")"
test -x "$jdk_path/bin/javac"
"${admin[@]}" ln -sfnT "$jdk_path" /opt/homebudget/jdk17
export JAVA_HOME=/opt/homebudget/jdk17
export ANDROID_HOME=/opt/homebudget/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/12.0/bin:$PATH"
java -version
javac -version

# Install the pinned Google command-line tools, verifying Google's repository checksum.
if [[ ! -x "$ANDROID_HOME/cmdline-tools/12.0/bin/sdkmanager" ]]; then
  python3 - <<'PY'
import hashlib, os, pathlib, tempfile, urllib.request, xml.etree.ElementTree as ET, zipfile, shutil
base = 'https://dl.google.com/android/repository/'
archive = 'commandlinetools-linux-11076708_latest.zip'
with urllib.request.urlopen(base + 'repository2-1.xml', timeout=120) as response:
    root = ET.fromstring(response.read())
expected = None
algorithm = None
for complete in root.iter('complete'):
    if complete.findtext('url') == archive:
        checksum = complete.find('checksum')
        expected = checksum.text.strip()
        algorithm = checksum.attrib.get('type', 'sha1')
        break
if expected is None:
    raise SystemExit('Pinned command-line tools not found in Google metadata; review setup version.')
sdk = pathlib.Path(os.environ['ANDROID_HOME'])
with tempfile.TemporaryDirectory(prefix='android-setup-') as temp:
    download = pathlib.Path(temp) / 'tools.zip'
    with urllib.request.urlopen(base + archive, timeout=120) as response, download.open('wb') as out:
        shutil.copyfileobj(response, out)
    digest = hashlib.new(algorithm)
    with download.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    if digest.hexdigest() != expected:
        raise SystemExit('Command-line tools checksum mismatch')
    with zipfile.ZipFile(download) as zipped:
        zipped.extractall(pathlib.Path(temp) / 'unpacked')
    target = sdk / 'cmdline-tools' / '12.0'
    target.mkdir(parents=True, exist_ok=True)
    shutil.copytree(pathlib.Path(temp) / 'unpacked' / 'cmdline-tools', target, dirs_exist_ok=True)
    for executable in (target / 'bin').iterdir():
        executable.chmod(0o755)
PY
fi
sdkmanager="$ANDROID_HOME/cmdline-tools/12.0/bin/sdkmanager"

# yes may exit 141 (SIGPIPE); only sdkmanager's status determines success.
set +e
yes | "$sdkmanager" --sdk_root="$ANDROID_HOME" --licenses
license_status=${PIPESTATUS[1]}
set -e
if [[ "$license_status" -ne 0 ]]; then exit "$license_status"; fi
"$sdkmanager" --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;34.0.0'
test -f "$ANDROID_HOME/platforms/android-35/android.jar"
test -x "$ANDROID_HOME/build-tools/34.0.0/aapt2"

cat > .codex/android-env.sh <<'ENV'
export JAVA_HOME=/opt/homebudget/jdk17
export ANDROID_HOME=/opt/homebudget/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/12.0/bin:$PATH"
ENV
"${admin[@]}" install -m 0644 .codex/android-env.sh /etc/profile.d/homebudget-android.sh
# Update only sdk.dir; preserve any other local.properties entries.
python3 - <<'PY'
from pathlib import Path
p = Path('local.properties')
lines = p.read_text().splitlines() if p.exists() else []
lines = [line for line in lines if not line.lstrip().startswith('sdk.dir=')]
lines.append('sdk.dir=/opt/homebudget/android-sdk')
p.write_text('\n'.join(lines) + '\n')
PY
chmod +x gradlew
mkdir -p .codex/logs
./gradlew --version | tee .codex/logs/gradle-version.log
# Compile during setup while downloads are available and populate Gradle caches.
./gradlew assembleDebug --no-daemon --stacktrace --console=plain 2>&1 | tee .codex/logs/assembleDebug.log
test -s app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
sha256sum app/build/outputs/apk/debug/app-debug.apk | tee .codex/logs/apk.sha256
