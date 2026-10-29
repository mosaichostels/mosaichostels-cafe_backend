Read when: debugging, deploying, editing hooks, or before touching prod config.
Verified: 2026-10-29 @ 9e39a6f
Covers: ../AGENTS.md, ../docs/backend/AGENTS.md, .claude/settings.json, .codex/hooks.json, src/main/java/com/hostel/ordering/service/OrderService.java

# Gotchas

- Workspace coupling: rules, agents, skills and hook scripts live in the parent folder (not a repo, not on GitHub): `../AGENTS.md`, `../docs/backend/AGENTS.md`, `../.claude/{agents,skills,hooks}`.
- `.claude/settings.json` hooks reference `$CLAUDE_PROJECT_DIR/../.claude/hooks/backend-*.sh` in the parent folder: `backend-guard-prod-config.sh` (PreToolUse; blocks edits to `application-prod.yml`, exit 2) and `backend-compile-check.sh` (PostToolUse on `*.java`, runs `mvn -q -o compile`, 120 s timeout; needs `jq` and an offline-ready `~/.m2`). Both exist and are executable. They break if the repo moves away from the workspace.
- `.codex/hooks.json` is gitignored and calls `/Users/naveen/.agents/hooks/project_sync.py pre|post` (absolute; exists). Different from Claude hooks, so kept. `.agents/` does not exist here.
- Push to `main` force-pushes to the live Space after CI passes; a bad merge hits production.
- Space sleeps without inbound traffic; `HeartbeatTask` and Docker HEALTHCHECK do not prevent it, so scheduled jobs (stale charge recovery, audit cleanup) may not fire while asleep.
- Production CORS preflight is answered by the HF proxy; test origins with a real request.
- No base `application.yml`: local run setup unknown; `CORS_ALLOWED_ORIGINS` and `MONGODB_URI` have no prod default, startup fails without them.
- `FirebaseConfig` fails startup without credentials.
- `lazy-initialization: true` in prod: wiring errors can surface at first use, not at boot.
- Rate-limit state is in-memory and per instance; resets on restart.
- Do not rename `ezee` charge-post statuses or `UNCONFIRMED:` prefix: stored on orders and read by clients.
- Run Maven on JDK 17 (`JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`). The default Homebrew `mvn` uses JDK 27, where Lombok 1.18.42 fails with `ClassNotFoundException: com.sun.tools.javac.tree.EndPosTable` during compile.
