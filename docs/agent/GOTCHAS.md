# Gotchas

- Rules, agents, skills and hook scripts live in the parent workspace folder (not a repo, not on GitHub): `../AGENTS.md`, `../docs/backend/AGENTS.md`, `../.claude/{agents,skills,hooks}`. `.claude/settings.json` here calls `$CLAUDE_PROJECT_DIR/../.claude/hooks/backend-guard-prod-config.sh` (PreToolUse) and `backend-compile-check.sh` (PostToolUse, runs `mvn -q -o compile`); both exist and are executable. They break if this repo is moved away from the workspace.
- `.codex/hooks.json` is gitignored and calls `/Users/naveen/.agents/hooks/project_sync.py` (absolute, exists). Different from the Claude hooks, so it is kept.
- Push to `main` force-deploys to the live Space after CI passes.
- The Space sleeps without inbound traffic; `HeartbeatTask` and Docker HEALTHCHECK do not prevent it; scheduled jobs may not fire.
- Production CORS preflight is answered by the HF proxy; test origins with a real request.
- No base `application.yml`; local run setup unknown.
