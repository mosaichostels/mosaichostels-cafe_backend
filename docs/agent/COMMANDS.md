# Commands

Not run in this pass (no build executed).
- Build + test (CI uses this): `mvn -B verify`
- Compile only (the Claude hook uses this): `mvn -q -o compile`
- Package: `mvn clean package -DskipTests` (Dockerfile)
- Docker run: image listens on 7860 with profile `prod`
- Deploy: push to `main` (GitHub Action, needs secret `HF_TOKEN`). Local run command / dev profile: unknown (no `application.yml`; only `application-prod.yml`).
