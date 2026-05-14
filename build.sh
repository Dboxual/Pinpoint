#!/usr/bin/env bash
# Quick build script — compiles sources and packages the jar
set -e
BASE="$(cd "$(dirname "$0")" && pwd)"
LIBS="$BASE/libs"
OUT="$BASE/build/classes"
JAR="$BASE/build/WaypointSystem-1.0.3.jar"

rm -rf "$OUT" && mkdir -p "$OUT"
find "$BASE/src/main/java" -name "*.java" > "$BASE/build/sources.txt"

CP="$LIBS/paper-api.jar:$LIBS/vault-api.jar:$LIBS/adventure-api.jar:$LIBS/adventure-key.jar:$LIBS/jetbrains-annotations.jar:$LIBS/guava.jar:$LIBS/examination-api.jar:$LIBS/bungeecord-chat.jar"

javac --release 21 -cp "$CP" -d "$OUT" @"$BASE/build/sources.txt"
cp "$BASE/src/main/resources/plugin.yml" "$OUT/"
cp "$BASE/src/main/resources/config.yml" "$OUT/"
cd "$OUT" && jar cf "$JAR" .
echo "Built: $JAR ($(du -sh "$JAR" | cut -f1))"
