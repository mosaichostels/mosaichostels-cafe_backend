#!/usr/bin/env bash
# PostToolUse gate: any .java edit must still compile.
# Lombok generation and Spring wiring errors only surface at compile time,
# so catching them here keeps a broken tree from being handed back as "done".
set -uo pipefail

file_path=$(jq -r '.tool_input.file_path // empty')
case "$file_path" in
  *.java) ;;
  *) exit 0 ;;
esac

cd "$CLAUDE_PROJECT_DIR" || exit 0

output=$(mvn -q -o compile 2>&1)
if [ $? -ne 0 ]; then
  echo "mvn compile failed after editing $file_path:" >&2
  echo "$output" | grep -E '^\[ERROR\]' | head -20 >&2
  exit 2
fi
exit 0
