# Structure

- `src/main/java/com/hostel/ordering/`: `config/`, `controller/`, `dto/`, `exception/`, `ezee/`, `model/`, `repository/`, `security/`, `service/`
- `src/main/resources/`: `application-prod.yml` (only config file; no base `application.yml`), `mongodb-init.js`
- `src/test/java/com/hostel/ordering/`: mirrors packages (config, controller, dto, ezee, model, repository, security, service); no `src/test/resources`
- `.github/workflows/sync_to_hf.yml`: CI + deploy
- `.claude/settings.json`: hooks (see GOTCHAS.md); `.codex/hooks.json`: gitignored, local
- `Dockerfile`, `pom.xml`, `README.md` (also HF Space metadata front matter), `target/` (ignored)
