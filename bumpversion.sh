#!/bin/sh

set -eu

GRADLE_FILE="app/build.gradle"

if [ ! -f "$GRADLE_FILE" ]; then
  echo "error: $GRADLE_FILE not found" >&2
  exit 1
fi

version_code=$(awk '/versionCode[[:space:]]+[0-9]+/{print $2; exit}' "$GRADLE_FILE")
version_name=$(awk -F'"' '/versionName[[:space:]]+"[0-9]+"/{print $2; exit}' "$GRADLE_FILE")

if [ -z "${version_code:-}" ] || [ -z "${version_name:-}" ]; then
  echo "error: could not find versionCode/versionName in $GRADLE_FILE" >&2
  exit 1
fi

new_version_code=$((version_code + 1))
new_version_name=$((version_name + 1))

sed -i '' "s/versionCode [0-9]*/versionCode $new_version_code/" "$GRADLE_FILE"
sed -i '' "s/versionName \"[0-9]*\"/versionName \"$new_version_name\"/" "$GRADLE_FILE"

git add "$GRADLE_FILE"
git commit -m "bump version to $new_version_name"
git tag "v$new_version_name"

echo "Bumped version to $new_version_name"
