#!/bin/bash
# Remap the built 1.8.9 Celeritas jar from Ornithe intermediary to Mojang
# official names, so the patcher can target Tessera's ordinary 1.8.9 client jar.
#
# Run  ./gradlew :ornithe:packageJar
# first, then this script.  The result is patched-client/build/celeritas-official.jar.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(./gradlew -q properties 2>/dev/null | awk '/^version:/ {print $2}' | head -1)
if [ -z "$VERSION" ]; then
  VERSION=$(grep '^project_base_version=' gradle.properties | cut -d= -f2)
  VERSION="$VERSION-dev"
fi
INPUT="build/libs/$VERSION/celeritas-fabriclike-mc8.9-$VERSION.jar"
OUTPUT="patched-client/build/celeritas-official.jar"
TESSERA_DATA="${TESSERA_DATA:-/home/ygbs/桌面/Tessera/target/debug/data}"
GAME="$TESSERA_DATA/游戏"

if [ ! -f "$INPUT" ]; then
  echo "missing $INPUT; build :ornithe:packageJar first" >&2
  exit 1
fi
mkdir -p patched-client/build

MAPPINGS=$(find "$HOME/.gradle/caches/fabric-loom/1.8.9" -name mappings.tiny | head -1)
MCJAR=$(find "$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-calamus-gen1-intermediary-v2-intermediary" -name '*.jar' | head -1)
TR=$(find "$HOME/.gradle/caches/modules-2/files-2.1/net.fabricmc/tiny-remapper" -name 'tiny-remapper-*.jar' | sort | tail -1)
MIO=$(find "$HOME/.gradle/caches/modules-2/files-2.1/net.fabricmc/mapping-io" -name 'mapping-io-*.jar' | sort | tail -1)
ASM=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.ow2.asm" -path '*9.10.1*' -name '*.jar' | tr '\n' ':')
MIXIN=$(find "$HOME/.gradle/caches/modules-2/files-2.1/net.fabricmc/sponge-mixin" -name '*.jar' | sort | tail -1)
MIXEXTRA=$(find "$HOME/.gradle/caches/modules-2/files-2.1/io.github.llamalad7/mixinextras-fabric" -name '*.jar' | sort | tail -1)
FAST=$(find "$GAME/libraries/it/unimi/dsi/fastutil" -name '*.jar' | sort | tail -1)

if [ -z "$MAPPINGS" ] || [ -z "$MCJAR" ] || [ -z "$TR" ] || [ -z "$MIO" ] || [ -z "$ASM" ]; then
  echo "could not locate remapping inputs in the Gradle cache" >&2
  exit 1
fi

CP="$(dirname "$0")/build/remap-classes"
mkdir -p "$CP"
javac -cp "$TR:$MIO:$ASM" -d "$CP" patched-client/tools/RemapCeleritas.java

mapfile -t LIBS < <(python3 - "$GAME/versions/1.8.9/1.8.9.json" "$GAME" <<'PY'
import json,sys,os
j=json.load(open(sys.argv[1])); game=sys.argv[2]
for l in j.get('libraries',[]):
    d=l.get('downloads',{}); a=d.get('artifact')
    if a: print(os.path.join(game,'libraries',a['path']))
    for cls,cd in (d.get('classifiers') or {}).items():
        if 'linux' in cls: print(os.path.join(game,'libraries',cd['path']))
PY
)

java -cp "$CP:$TR:$MIO:$ASM" RemapCeleritas "$INPUT" "$OUTPUT" "$MAPPINGS" intermediary official \
  "$MCJAR" "${LIBS[@]}" "$FAST" "$MIXIN" "$MIXEXTRA"
echo "$OUTPUT"
