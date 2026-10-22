# Conventions

From backend AGENTS (not independently re-verified in code beyond file existence):
- Never interpolate user input into `$regex`; use `Pattern.quote`.
- Soft delete for `MenuItem`/`OtherEssential` (`deleted` flag, repositories filter it); dormitories hard-delete.
- Declare indexes on models (`auto-index-creation: true` in prod yml).
- Error responses carry no internal detail; use `IllegalArgumentException` for messages callers may read.
- Method-level `@PreAuthorize` overrides HTTP `permitAll()`; guest POST /orders needs `or isAnonymous()`.
- Never edit `application-prod.yml` without the user (a hook blocks it; deploys on push to main).
- Controllers are the API contract source of truth for frontend `js/api.js` and Android `ApiService.java`.
- Code review for eZee changes: workspace agent `backend-ezee-charge-reviewer`.
