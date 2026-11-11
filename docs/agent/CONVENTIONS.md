Read when: writing or reviewing code here.
Verified: 2026-10-29 @ 9e39a6f. Items marked (AGENTS) come from backend AGENTS (`../docs/backend/AGENTS.md`) and were not all re-derived from code.
Covers: ../docs/backend/AGENTS.md, src/main/java/**/*.java, src/main/resources/**

# Conventions

- Never interpolate user input into `$regex`; use `Pattern.quote` (AGENTS).
- Soft delete (`deleted` flag, repos filter it) for `MenuItem`/`OtherEssential`; dormitories hard-delete (AGENTS).
- Declare indexes on models; `auto-index-creation: true` in prod yml makes them real (application-prod.yml:21).
- Error bodies carry no internal detail (`GlobalExceptionHandler`, `ErrorResponse`); throw `IllegalArgumentException` only for messages callers may read (AGENTS).
- Method-level `@PreAuthorize` overrides HTTP `permitAll()`; guest `POST /orders` needs `or isAnonymous()` (OrderController:32).
- New protected endpoint: add `@PreAuthorize`; default is authenticated (SecurityConfig:81).
- Never edit `application-prod.yml` without the user (hook blocks it; deploys on push).
- Controllers are the contract source of truth for frontend `js/api.js` and Android `ApiService.java`; changing a route/shape needs sibling-repo follow-up.
- eZee changes: review double-charge prevention, response parsing and the no-void constraint by hand.
- Mark deliberate shortcuts with `ponytail:` comments (e.g. RateLimitFilter:24).
- Tests: JUnit 5 + Mockito, one test class per unit, named for the behaviour (TESTING.md).
