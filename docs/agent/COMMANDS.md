Read when: building, testing, running, deploying or debugging hooks.
Verified: 2026-10-29 @ 8b57217. Commands below were read from CI/Dockerfile/hooks; not run in this refresh.
Covers: pom.xml, Dockerfile, .github/workflows/**, .claude/**, .codex/**

# Commands

| Goal | Command | Source |
| --- | --- | --- |
| Build + all tests (CI) | `mvn -B verify` | sync_to_hf.yml |
| Compile only (Claude hook) | `mvn -q -o compile` (offline) | ../.claude/hooks/backend-compile-check.sh |
| One test class | `mvn -q test -Dtest=OrderServiceTest` (standard surefire; not run) | pom |
| Package jar | `mvn clean package -DskipTests -q` | Dockerfile:7 |
| Run packaged | `java -Dspring.profiles.active=prod -jar target/*.jar` needs prod env vars (ENVIRONMENT.md) | Dockerfile entrypoint |
| Docker | image listens on 7860, profile `prod`, `-Xmx4g` | Dockerfile |
| Health | `GET /health` | HealthController |
| Seed config data | `mongo hostel_ordering mongodb-init.js` (manual, idempotent inserts) | src/main/resources/mongodb-init.js:4 |
| Deploy | push to `main`; needs GitHub secret `HF_TOKEN` | sync_to_hf.yml |

- Local dev run / dev profile: unknown (no `application.yml`; only `application-prod.yml`; `ezee.mock` flag exists, see ENVIRONMENT.md).
- Hook scripts live in the parent workspace: `../.claude/hooks/`.
