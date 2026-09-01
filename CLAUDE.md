# mosaichostels-cafe_backend

Spring Boot backend for the Mosaic Hostels cafe ordering system. Deployed as
a Docker image to Hugging Face Spaces (16GB CPU Basic space, port 7860,
kept awake via heartbeats).

**Stack:** Java 17, Spring Boot 3.5.10, Spring Data MongoDB, Spring Security,
JWT (jjwt 0.11.5), Lombok, Firebase Admin SDK (FCM push). Maven build
(`com.hostel:ordering-system`).

## Package layout (`src/main/java/com/hostel/ordering/`)

- `controller/` — `AuthController`, `OrderController`, `MenuItemController`,
  `OtherEssentialController`, `UserController`, `ConfigController`,
  `AuditController`, `NotificationController`, `HealthController`
- `service/` — business logic: `OrderService`, `CategoryService`,
  `UserService`, `AuthService`, `DormitoryService`, `OrderStatusService`,
  `FCMNotificationService`, `AuditService`
- `model/` — `Order`, `OrderItem`, `MenuItem`, `Category`, `User`,
  `Dormitory`, `AuditLog`, `OrderStatusConfig`, `OtherEssential`
- `repository/` — Spring Data MongoDB repositories (`OrderRepository` +
  custom `OrderRepositoryImpl`/`OrderRepositoryCustom` for query-builder
  search), one per model
- `security/` — `JwtUtils`, `AuthTokenFilter`, `UserDetailsServiceImpl`,
  `SecurityConfig` (Spring Security + JWT filter chain)
- `ezee/` — eZee PMS integration: `EzeeClient`, `EzeeChargePostService`,
  `EzeeXmlUtil`. This is the highest-risk integration point (double-charge
  prevention, response parsing, no-void constraint — see
  `.claude/agents/ezee-charge-reviewer.md` before touching it)
- `config/` — `WebConfig` (CORS), other `@Configuration` beans
- `exception/` — `@ControllerAdvice` global error handling

`Order` is the most connected model (75 edges) — most changes ripple through
`OrderService` → `EzeeChargePostService` → FCM notification.

## Data

MongoDB (Spring Data MongoDB / `MongoRepository` + `MongoTemplate` for
custom queries). No SQL.

## Auth

JWT-based, `Bearer` token, filter chain in `security/SecurityConfig.java` +
`AuthTokenFilter`. `AuthController` issues tokens, `AuthService` validates.

## eZee PMS integration

Order charges post to the property's eZee PMS via `ezee/EzeeChargePostService`
and `EzeeClient`. Charges cannot be voided once posted — the double-charge
guard matters more than almost anything else in this codebase. See
`docs/ezee-charge-posting-integration.md` in the `Website` sibling repo and
`.claude/skills/ezee-integration/SKILL.md` here before changing this path.

**Design: admin-mapped manual posting, not automatic.** No algorithmic
name/room matching — explicitly rejected as spoofable in shared dorms.
Flow: admin opens the frontend admin web (`js/admin.js`), for a `DELIVERED`
order clicks "Post & Check", searches eZee's live occupant data via
`GET /orders/{id}/ezee-candidates` (plain passthrough of eZee's
`roomlist`/`roomquery`, no matching logic), picks the guest/room, confirms.
`POST /orders/{id}/chargepost {room}` then re-resolves the live folio via
`roomquery(room)` (never cached from the search step), posts via
`chargepost`, and on success sets `order.status = "CHECKED"`.
`chargePostStatus` is `null` (never posted) | `QUEUED` (eZee accepted) |
`FAILED` (eZee rejected or no live folio) | `VOIDED` (order cancelled after
posting — `updateOrderStatus()` calls `EzeeChargePostService.voidPost(order)`
on transition to `CANCELLED` when status was `QUEUED`; on void failure the
status stays `QUEUED`, never claim a void that didn't happen).
`createOrder()` makes no eZee call. Scope is backend + frontend only — no
`mosaichostels-cafe_android` changes, ever, for this feature.

**AddExtraCharge API quirks** (undocumented, found from raw production
responses, not the API docs):
- Success responses use `"Errors"` (plural, `ErrorCode: "0"`); rejections
  use `"Error"` (singular). Parsing only `"Errors"` makes every rejection
  silently parse as success — this shipped once and posted zero charge
  while marking the order `CHECKED`.
- A `roomquery` scoped to a single room returns `roomrows/row` entries with
  **no `room` field** — only the top-level flat response has `<room>`.
  Filtering by `row.get("room").equals(room)` always yields empty and fails
  every chargepost with "No occupant found".
- `HotelCode` is required in the `AddExtraCharge` `Authentication` block —
  this property's code is `57677`. Food Charge extra-charge item id:
  `5767700000000000003` (`EZEE_FOOD_CHARGE_ID`).
- **No void API.** AddExtraCharge has no corresponding void/remove endpoint
  (unlike POS2PMS's voidcharge). Chargepost voids are manual: staff must
  remove the charge line from the guest's folio directly in eZee PMS. The
  Order stays marked `QUEUED` after a void failure so the admin knows the
  charge is still live (never claim a void that didn't happen).

When touching `EzeeClient`/`EzeeChargePostService`, pull real raw responses
from HF Space logs first (`hf spaces logs mosaichostels/cafe_backend -n
500`) rather than trusting assumed shapes. GitHub push to `main` auto-syncs
to the HF Space via `.github/workflows/sync_to_hf.yml`.

## Notifications

Firebase Admin SDK → FCM push via `FCMNotificationService` /
`NotificationController`.

## Tooling in this repo

- `.claude/agents/security-reviewer.md` — auth/JWT/security review
- `.claude/agents/ezee-charge-reviewer.md` — eZee charge-posting invariants
- `.claude/hooks/guard-prod-config.sh` — `PreToolUse` guard on Edit/Write/MultiEdit
- `.claude/hooks/compile-check.sh` — `PostToolUse`, compiles after every edit (120s timeout)
- `.claude/skills/ezee-integration/` — eZee integration playbook
- `.claude/skills/deploy-hf/` — Hugging Face Spaces deploy
- `.mcp.json` — context7 (docs) + mongodb MCP server
- Companion repos: `../mosaichostels-cafe_frontend` (guest/staff web UI),
  `../mosaichostels-cafe_android` (staff Android app) — both consume this
  API; check `ApiService.java` (Android) or `js/api.js` (frontend) against
  the matching controller before changing a response shape.
