#!/usr/bin/env bash
# PreToolUse guard: application-prod.yml carries every eZee credential
# placeholder and MONGODB_URI. A push to main force-deploys it to the
# Hugging Face Space, so a bad value here reaches production with no review.
set -uo pipefail

file_path=$(jq -r '.tool_input.file_path // empty')
case "$file_path" in
  *src/main/resources/application-prod.yml)
    echo "Blocked: application-prod.yml is the production config and deploys on the next push to main. Ask the user to confirm and make the edit themselves, or have them re-run with this hook disabled." >&2
    exit 2
    ;;
esac
exit 0
