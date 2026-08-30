---
name: deploy-hf
description: Deploy this backend to the Hugging Face Space mosaichostels/cafe_backend.
disable-model-invocation: true
---

# Deploy to Hugging Face Spaces

Deployment is automatic: pushing to `main` triggers
`.github/workflows/sync_to_hf.yml`, which builds, runs the test suite, and
then force-pushes the repo to
`huggingface.co/spaces/mosaichostels/cafe_backend`. The Space rebuilds from
the `Dockerfile` and restarts.

**The push is a `git push --force`.** It overwrites the Space's history. It
also restarts the live ordering backend used by the hostel cafe.

## Deploy

1. Confirm with the user before pushing. This restarts production.
2. Verify the tree is clean and tests pass locally:
   ```bash
   git status --short
   mvn -q test
   ```
3. Push:
   ```bash
   git push origin main
   ```
4. Watch the workflow:
   ```bash
   gh run watch
   ```

## Verify

Health endpoint is unauthenticated:

```bash
curl -fsS https://mosaichostels-cafe-backend.hf.space/health
```

Spring's lazy initialization means the first request after a restart is slow.
A timeout on the first curl is not automatically a failed deploy — retry once
before investigating.

## Rollback

There is no rollback button. Revert the offending commit on `main` and push
again; the workflow redeploys the reverted state.

```bash
git revert <sha>
git push origin main
```

## Manual re-deploy

Re-run without a new commit from the Actions tab (`workflow_dispatch`), or:

```bash
gh workflow run sync_to_hf.yml
```

## Secrets

`HF_TOKEN` is a GitHub Actions secret. Runtime config (`MONGODB_URI`,
`EZEE_AUTH_CODE`, `EZEE_HOTEL_CODE`, `EZEE_FOOD_CHARGE_ID`,
`EZEE_ESSENTIAL_CHARGE_ID`, `CORS_ALLOWED_ORIGINS`) lives in the Space's own
settings, not in this repo. Changing them requires a Space restart, not a
push.
