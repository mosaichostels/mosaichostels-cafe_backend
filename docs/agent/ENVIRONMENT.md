Read when: configuring, deploying, or debugging startup. Names only; no values read.
Verified: 2026-11-11 @ 2626fed + final review fixes (source: application-prod.yml and `@Value`/`getenv` in src/main)
Covers: src/main/resources/application-prod.yml, src/main/java/**/*.java, Dockerfile

# Environment

| Name | Where used | Notes |
| --- | --- | --- |
| `PORT` | application-prod.yml:2 | default 7860 |
| `MONGODB_URI` | yml:18 | required, no default |
| `CORS_ALLOWED_ORIGINS` | yml:43 | required in prod, comma-separated, no wildcard |
| `EZEE_ENDPOINT`, `EZEE_KIOSK_ENDPOINT` | yml:47-48 | defaults to eZee live URLs |
| `EZEE_AUTH_CODE`, `EZEE_HOTEL_CODE` | yml:49-50 | default empty |
| `EZEE_FOOD_CHARGE_ID`, `EZEE_ESSENTIAL_CHARGE_ID` | yml:51-52 | default empty |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | FirebaseConfig:38 | falls back to classpath `firebase-service-account.json` (not in repo); no credentials -> startup fails |
| `HF_TOKEN` | GitHub Actions secret | deploy only |

Properties with no env mapping in the yml (set elsewhere or defaults):
| Property | Default / note |
| --- | --- |
| `config.admin.username` | default `admin` |
| `config.admin.password` | default none (HostelOrderingApplication:19); env `CONFIG_ADMIN_PASSWORD` (HostelOrderingApplication:31, backend AGENTS) |
| `config.jwtSecret` | default empty, startup fails if under 32 bytes; env `CONFIG_JWTSECRET` (JwtUtils:39) |
| `config.jwtExpirationMs` | 3600000 |
| `config.jwtRefreshGraceMs` | 31536000000 |
| `audit.retention-days` | 90 |
| `pms.enabled` | hub kill switch; `false` throws before any eZee I/O (code default on); env `PMS_ENABLED` |
| `pms.hub-per5s`, `pms.cafe-reserve` | rate-limit window shares (defaults 2 and 2); env `PMS_HUB_PER5S`, `PMS_CAFE_RESERVE` |
| `pms.cafe-quiet-ms` | hub reads yield this long after any café eZee call (default 3000); env `PMS_CAFE_QUIET_MS` |
| `pms.writes.<feature>` | write flag per feature, default off; NOT settable by a per-feature env var (see below) |
| `pms.canary.<feature>` | canary list per feature |
| `pms.writes.room-status`, `pms.canary.room-status` | clean/dirty write to eZee (`POST /pms/housekeeping/rooms/{roomId}/status`); flag absent = off, no default turns it on; canary = list of eZee ROOM ids (not booking ids). The room-status write FAILS CLOSED: flag on with an empty or missing canary list refuses every room (403), so set the canary first. Set via `SPRING_APPLICATION_JSON` (see below) |
| `pms.ttl.<name>` | cache TTL seconds; env `PMS_TTL_<NAME>` for single-word names. Names in use: `hk` (30), `departures` (60, cleaning to-do), `bills` (60) |
| `app.android.latest-version-code`, `app.android.min-version-code` | `AppVersionController` `@Value`, default 0; env `APP_ANDROID_LATEST_VERSION_CODE`, `APP_ANDROID_MIN_VERSION_CODE`; not in `application-prod.yml`; a non-numeric value fails context start (the app does not boot) |
| `app.android.apk-url` | default empty; env `APP_ANDROID_APK_URL`; not in `application-prod.yml` (all three unset = `{0,0,""}`, no update prompt) |
| `ezee.base-url` | optional; gateway also reads existing `ezee.auth-code`, `ezee.hotel-code` (`PmsProperties`, `EzeeGateway`) |

Per-feature maps (`pms.writes.<feature>`, `pms.canary.<feature>`): env var names cannot produce hyphenated or camel-case
map keys (`PMS_WRITES_ROOM_STATUS` binds `room.status`, not `room-status`). Set them with ONE secret
`SPRING_APPLICATION_JSON`, e.g. `{"pms":{"writes":{"room-status":true},"canary":{"room-status":["<roomId>"]}}}`,
or an owner-approved `pms:` block in `application-prod.yml`, or the planned runtime override layer in `PmsFlags` (P6).
| `ezee.mock` | `false` in prod yml |
| `cors.allowed-origins` | code default `http://localhost:3000` (WebConfig:10) |

Where secrets are stored (HF Space settings, GitHub secrets): unknown. Local .env: none tracked.
