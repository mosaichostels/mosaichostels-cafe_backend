Read when: you must find where code lives or add a file.
Verified: 2026-11-11 @ 2626fed + final review fixes
Covers: src/main/java/**/*.java, src/main/resources/**, src/test/java/**/*.java, scripts/**, pom.xml

# Structure

Base package `src/main/java/com/hostel/ordering/` (all paths below relative to it):

| Dir | Contents |
| --- | --- |
| `HostelOrderingApplication.java` | entry; `@EnableAsync`, `@EnableScheduling`; `CommandLineRunner init` seeds initial admin via `AuthService.registerInitialAdmin` |
| `config/` | `SecurityConfig`, `WebConfig` (CORS), `RateLimitFilter`, `RequestLoggingFilter`, `FirebaseConfig`, `HeartbeatTask` |
| `controller/` | Audit, AppVersion (`GET /app/android-version`), Auth, Config, Health, MenuItem, Notification, Order, OtherEssential, User |
| `service/` | Audit(+CleanupTask), Auth, Category, ConfirmToken, Dormitory, FCMNotification, Idempotency, MenuItem, Order, OrderStatus, OtherEssential, User |
| `repository/` | Spring Data repos; `OrderRepositoryCustom`/`OrderRepositoryImpl` hold atomic claim + stale queries + `findForChargeReconcile` |
| `model/` | Mongo documents (see ARCHITECTURE.md), `ErrorResponse` |
| `security/` | `AuthTokenFilter`, `JwtUtils`, `LoginThrottle`, `AuthUserDetails`, `UserDetailsServiceImpl` |
| `ezee/` | `EzeeClient`, `EzeeChargePostService`, `EzeeXmlUtil`, `EzeeMockResponses`, `RoomQueryResult`, `EzeeUnavailableException` |
| `pms/` | staff-hub eZee foundation: `EzeeGateway` (+`EzeeFamily`, `PmsTransport`, `JdkPmsTransport`), `PmsRateLimiter`/`PmsPriority`, `PmsCache`, `PmsProperties`, `PmsFlags`, `PmsWriteGuard`/`PmsWriteClaim`/`PmsWriteClaimStore` (+`MongoPmsWriteClaimStore`), `PmsStatusController`, `PmsUnconfirmedController`, `Pms*Exception`. P1 read-only hub: `PmsFrontDeskController`/`PmsFrontDeskService` (arrivals, departures, in-house), `PmsBookingService`, `PmsGuestService`, `PmsHousekeepingController`/`PmsHousekeepingService`, `PmsHousekeepingFeed` (shared cached InhouseRoomStatus), `PmsReconcileController`/`PmsReconcileService`, helpers `PmsReads`, `PmsJson`, `PmsDates`, `PmsIds`, `PmsCsv`; `pms/dto/` records `BookingSummary`, `BookingDetail`, `FolioSummary`, `GuestHit`, `RoomStatus`, `ChargeCheck` |
| `dto/` | `CreateOrderRequest` only |
| `exception/` | `GlobalExceptionHandler`, `ChargeUnconfirmedException` |

Other paths:
- `src/main/resources/`: `application-prod.yml` (only config; no base `application.yml`), `mongodb-init.js` (manual seed script: categories, dormitories; run by hand, not by the app)
- `src/test/java/com/hostel/ordering/`: mirrors packages; no `src/test/resources`
- `scripts/promote-superadmin.sh`: sets one user to `ROLE_SUPERADMIN` and bumps `tokensValidFrom` (needs `MONGODB_URI`, `mongosh`)
- `.github/workflows/sync_to_hf.yml`: CI + deploy. `Dockerfile`, `pom.xml`, `README.md` (HF front matter)
- `.claude/settings.json`: tracked hooks (GOTCHAS.md). `.codex/hooks.json`: gitignored, local. `target/`: build output, ignored

Do NOT touch without the user: `application-prod.yml` (hook-blocked, deploys on push), `sync_to_hf.yml`.
