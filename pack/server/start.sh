#!/bin/sh
# Random Item Skyblock dedicated server starter for Linux and macOS.
# Needs only curl or wget and tar. It downloads and verifies NeoForge, the server mods and,
# when Java 21 is not installed, a portable Eclipse Temurin 21 runtime into ./runtime.
# Run it with: sh start.sh
set -eu
cd "$(dirname "$0")"

NEOFORGE=21.1.265
JRE_BASE='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2'
USER_AGENT='zzythe/random-item-skyblock-server/0.6.1'

fail() { printf '\nCould not start: %s\n' "$1" >&2; exit 1; }

sha() { # sha <bits> <file>
    if command -v "sha${1}sum" >/dev/null 2>&1; then "sha${1}sum" "$2" | cut -d' ' -f1
    elif command -v shasum >/dev/null 2>&1; then shasum -a "$1" "$2" | cut -d' ' -f1
    else fail "sha${1}sum or shasum is needed to check downloads."; fi
}

download() { # download <url> <target> <bits> <expected hash>
    if [ -f "$2" ]; then
        [ "$(sha "$3" "$2")" = "$4" ] && return 0
        fail "$2 is different from the version this pack expects. Move it out of the way and start again."
    fi
    mkdir -p "$(dirname "$2")"
    echo "  downloading $(basename "$2")"
    if command -v curl >/dev/null 2>&1; then curl -fsSL -A "$USER_AGENT" -o "$2.part" "$1" || fail "download failed: $1"
    elif command -v wget >/dev/null 2>&1; then wget -q -U "$USER_AGENT" -O "$2.part" "$1" || fail "download failed: $1"
    else fail 'curl or wget is needed to download the server files.'; fi
    if [ "$(sha "$3" "$2.part")" != "$4" ]; then rm -f "$2.part"; fail "download check failed for $(basename "$2"). Try again later."; fi
    mv "$2.part" "$2"
}

is_java21() { [ -n "$1" ] && [ -x "$1" ] && "$1" -version 2>&1 | grep -q 'version "21[."]'; }

find_java() {
    for candidate in runtime/*/bin/java runtime/*/Contents/Home/bin/java "${JAVA_HOME:-/nonexistent}/bin/java" "$(command -v java 2>/dev/null || true)"; do
        if is_java21 "$candidate"; then JAVA=$candidate; return 0; fi
    done
    case "$(uname -s)-$(uname -m)" in
        Linux-x86_64|Linux-amd64) file=OpenJDK21U-jre_x64_linux_hotspot_21.0.13.1_2.tar.gz; hash=18f44fab9065217f3ba61d0eef8a7b5f3c3713aa7293001b0d4ac10ede3850f0 ;;
        Linux-aarch64|Linux-arm64) file=OpenJDK21U-jre_aarch64_linux_hotspot_21.0.13.1_2.tar.gz; hash=52bc58323d9266538a6361a3cf8365fc1186dd9510d7f33f623681c4aabb7e72 ;;
        Darwin-x86_64) file=OpenJDK21U-jre_x64_mac_hotspot_21.0.13.1_2.tar.gz; hash=10cee6ba4cd83fd4613a3f0bb3ef9dddd68c68d5f3ccfcc7e456707c9e1eafe0 ;;
        Darwin-arm64) file=OpenJDK21U-jre_aarch64_mac_hotspot_21.0.13.1_2.tar.gz; hash=848feb1906c7de79de4f8391636042193fbd46eff86e26299506c4656f62c260 ;;
        *) fail "no portable Java for $(uname -s) $(uname -m). Install a 64-bit Java 21 and run this again." ;;
    esac
    echo 'Java 21 was not found. Getting a portable Eclipse Temurin 21 runtime (about 50 MB) into the runtime folder.'
    download "$JRE_BASE/$file" "runtime/$file" 256 "$hash"
    tar -xzf "runtime/$file" -C runtime || fail 'could not unpack the Java runtime.'
    rm -f "runtime/$file"
    for candidate in runtime/*/bin/java runtime/*/Contents/Home/bin/java; do
        if is_java21 "$candidate"; then JAVA=$candidate; return 0; fi
    done
    fail 'the downloaded Java runtime did not start.'
}

accept_eula() {
    if [ -f eula.txt ] && grep -Eq '^[[:space:]]*eula[[:space:]]*=[[:space:]]*true[[:space:]]*$' eula.txt; then return 0; fi
    echo
    echo 'Minecraft servers require you to accept the Minecraft End User License Agreement:'
    echo '  https://aka.ms/MinecraftEULA'
    echo
    printf 'Have you read it, and do you accept it? Type yes to accept: '
    answer=''
    read -r answer || true
    case "$(printf '%s' "$answer" | tr '[:upper:]' '[:lower:]')" in
        yes|y) printf '# Accepted by the server owner at the start-up prompt on %s\neula=true\n' "$(date)" > eula.txt ;;
        *) echo 'The EULA was not accepted, so the server will not start.'; exit 2 ;;
    esac
}

echo 'Random Item Skyblock server'
sha 512 server-files.tsv >/dev/null
accept_eula
JAVA=''
find_java
echo "Using Java: $JAVA"
echo 'Checking server files...'
tab=$(printf '\t')
installer_name=''; installer_url=''; installer_sha=''
while IFS="$tab" read -r kind name url sum; do
    case "$kind" in ''|'#'*) continue ;; esac
    case "$name" in */*|*..*) fail "invalid file name in server-files.tsv: $name" ;; esac
    if [ "$kind" = mod ]; then download "$url" "mods/$name" 512 "$sum"
    elif [ "$kind" = installer ]; then installer_name=$name; installer_url=$url; installer_sha=$sum; fi
done < server-files.tsv

ARGS_FILE="libraries/net/neoforged/neoforge/$NEOFORGE/unix_args.txt"
if [ ! -f "$ARGS_FILE" ]; then
    echo "Installing NeoForge $NEOFORGE (first start only)..."
    download "$installer_url" "$installer_name" 512 "$installer_sha"
    "$JAVA" -jar "$installer_name" --installServer >/dev/null || fail 'the NeoForge installer failed. See the installer .log file.'
    [ -f "$ARGS_FILE" ] || fail 'the NeoForge installer did not finish. See the installer .log file.'
    rm -f "$installer_name"
fi

# The NeoForge installer writes a user_jvm_args.txt with every line commented out.
if ! grep -Eq '^[[:space:]]*-Xmx' user_jvm_args.txt 2>/dev/null; then
    gb=8
    if [ -r /proc/meminfo ]; then gb=$(awk '/^MemTotal:/ {print int($2 / 1048576)}' /proc/meminfo)
    elif command -v sysctl >/dev/null 2>&1; then gb=$(( $(sysctl -n hw.memsize) / 1073741824 )); fi
    max=$((gb - 2)); [ "$max" -gt 6 ] && max=6; [ "$max" -lt 2 ] && max=2
    printf '\n# Random Item Skyblock default. Change -Xmx to give the server more or less memory.\n-Xms2G\n-Xmx%sG\n' "$max" >> user_jvm_args.txt
fi

echo 'Starting the server. Type stop and press Enter to save and shut down.'
exec "$JAVA" @user_jvm_args.txt "@$ARGS_FILE" nogui "$@"
