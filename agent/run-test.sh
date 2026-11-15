#!/bin/bash
# Launch the official 1.8.9 client with the combined Celeritas/LWJGL3 agent.
# This is the manual equivalent of what Tessera does for a mdtLwjgl3 install.
set -euo pipefail
cd "$(dirname "$0")/.."

TESSERA_DATA="${TESSERA_DATA:-/home/ygbs/桌面/Tessera/target/debug/data}"
GAME="$TESSERA_DATA/游戏"
VERS="$GAME/versions/1.8.9"
JAVA="$TESSERA_DATA/运行时/28+24-ea-beta/bin/java"
AGENT="$(pwd)/agent/build/libs/celeritas-agent.jar"
MCJAR="$VERS/1.8.9.jar"
RUNDIR="${RUNDIR:-/tmp/celeritas-run}"
mkdir -p "$RUNDIR"

if [ ! -f "$AGENT" ]; then
  echo "missing $AGENT; build :agent:jar first" >&2
  exit 1
fi

CP=$(python3 - "$VERS/1.8.9.json" "$GAME" "$MCJAR" "$AGENT" <<'PY'
import json,sys,os
j=json.load(open(sys.argv[1])); game=sys.argv[2]
paths=[]
for l in j.get('libraries',[]):
    d=l.get('downloads',{}); a=d.get('artifact')
    if a: paths.append(os.path.join(game,'libraries',a['path']))
    for cls,cd in (d.get('classifiers') or {}).items():
        if 'linux' in cls: paths.append(os.path.join(game,'libraries',cd['path']))
paths += [sys.argv[3], sys.argv[4]]
print(':'.join(paths))
PY
)
FAST=$(find "$GAME/libraries/it/unimi/dsi/fastutil" -name '*.jar' | sort | tail -1)
if [ -z "$FAST" ]; then
  echo "fastutil missing from $GAME/libraries; the agent needs it on the classpath" >&2
  exit 1
fi
CP="$CP:$FAST"

exec "$JAVA" -Xmx2048M -Dfile.encoding=UTF-8 \
  -Dio.netty.native.workdir="$(dirname "$TESSERA_DATA")/tessera" \
  -Dmixin.debug.export=true -Dmixin.debug.export.decompile=false -javaagent:"$AGENT" -cp "$CP" \
  net.minecraft.client.main.Main \
  --username TestPlayer --version 1.8.9 \
  --gameDir "$RUNDIR" --assetsDir "$GAME/assets" --assetIndex 1.8 \
  --uuid 00000000-0000-0000-0000-000000000000 --accessToken 0 \
  --userProperties '{}' --userType legacy --width 854 --height 480
