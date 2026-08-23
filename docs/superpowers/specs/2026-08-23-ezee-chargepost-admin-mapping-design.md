# eZee Chargepost — Admin-Mapped Posting Design

Supersedes the auto-post sections of `docs/superpowers/plans/2026-08-22-ezee-chargepost-integration.md`. That plan's eZee transport layer (Task 3 XML parsing, Task 4 `EzeeClient` + mock mode) is unaffected and still applies as written. Everything about automatic trigger-on-creation, room-number hints, guest-name fuzzy matching, and the scheduled retry job is dropped.

## Goal

Cafe orders are never auto-posted to eZee. Instead, the existing admin billing web (`mosaichostels-cafe_frontend`, `js/admin.js`) gets a manual mapping step: for each `DELIVERED` order, an admin searches eZee's live guest/room data, picks the correct occupant, and posts the charge — in one action that also marks the order `CHECKED`.

## Why this over automatic matching

Automatic name/room matching (explored and rejected in this session) has irreducible ambiguity in shared dorms and is spoofable if guests supply their own room number. An admin who already knows which guest ordered what removes both problems at zero algorithmic risk, at the cost of one manual click per order — acceptable since billing already requires a human "Mark as Checked" step today.

## Architecture

- **Backend**: two new endpoints on top of the existing (unchanged) `EzeeClient`/mock-mode transport:
  - `GET /orders/{orderId}/ezee-candidates?dormitory=&room=&name=` — wraps `roomlist` (whole dormitory/room-type) or `roomquery` (specific room) depending on which filter is given, returns occupant rows (`room`, `guestname`, `masterfolio`) for the admin to browse/search. No matching logic — just a passthrough search.
  - `POST /orders/{orderId}/chargepost` — body `{ "room": "101" }` (or `{ "folio": "8" }` if the search already resolved one). Server calls `roomquery(room)` to get the authoritative live folio, posts the charge via `chargepost`, saves `chargePostStatus`/`chargePostRequestId`/`chargePostFolio`/`chargePostRoom`/`chargePostAt` on the `Order`, and sets `order.status = "CHECKED"` — all synchronously, in one HTTP call, so the admin UI gets a single pass/fail result.
- **`Order` model**: same 6 chargepost fields as the superseded plan (`chargePostStatus: null | "QUEUED" | "FAILED" | "VOIDED"`, `chargePostRequestId`, `chargePostError`, `chargePostRoom`, `chargePostFolio`, `chargePostAt`). No behavior change to the model itself.
- **`EzeeChargePostService`**: two methods only — `post(Order order, String room)` (admin-supplied room, no name-matching) and `voidPost(Order order)` (unchanged from the superseded plan — reverses a `QUEUED` charge on cancel). No `postAsync`, no `EzeeRoomResolver`, no scheduled retry.
- **`OrderService`**: `createOrder()` no longer touches eZee at all. `updateOrderStatus()` keeps the `CANCELLED` → `voidPost` guard (`"QUEUED".equals(order.getChargePostStatus())`) but the old `CHECKED`-time auto-post branch is deleted — `CHECKED` is now only ever set by the new `POST /orders/{id}/chargepost` endpoint on success, never directly.
- **Frontend (`admin.js`)**: `markAsChecked(orderId)` (line 658) is replaced by a `postAndCheck(orderId)` flow: opens a modal, calls the new search endpoint as the admin types a dormitory/room/name filter, admin picks a row, confirm calls `POST /orders/{id}/chargepost`. The old "Mark as Checked" button (line 611) becomes "Post & Check". `cancelOrder`/`confirmCancelOrder` (lines 636–652) are unchanged — void-on-cancel is a backend-only concern.

## Data flow

1. Guest/staff places order → `createOrder()` saves it, no eZee call. (Same as before this session's changes — order creation is untouched.)
2. Order reaches `DELIVERED` (kitchen/staff flow, unchanged).
3. Admin opens billing screen, sees "Post & Check" on the `DELIVERED` order, clicks it.
4. Modal lets admin search live eZee occupants by dormitory/room/name (`GET .../ezee-candidates`), pick one.
5. Admin confirms → `POST /orders/{id}/chargepost {room}` → backend resolves folio via `roomquery`, posts via `chargepost`, saves result, sets `CHECKED` on success.
6. Modal shows success (order now `CHECKED`) or the eZee error inline, letting the admin retry with a different room or try again later — order stays `DELIVERED` until a post succeeds.
7. If a `CHECKED` (posted) order is later cancelled, `voidPost` fires automatically as before, setting `VOIDED` on success.

## Error handling

- Every failure mode (room not found, credit limit, tax mismatch, network error) surfaces as a synchronous error response from `POST /orders/{id}/chargepost`; the frontend shows it in the modal and lets the admin pick a different room or cancel — no background retry, no silent failure state. `chargePostError` is saved for audit even though the UI already showed it live.
- Since posting only ever happens as a direct, admin-confirmed action, there is no "unresolvable" order state to design for — worst case the admin retries later, same as retrying any manual data-entry mistake today.

## Testing

- Backend: unit tests for the two new endpoints/service methods following the same TDD pattern as the superseded plan's Task 6/7 (mock `EzeeClient`, assert request fields and saved `Order` state on success/failure).
- Frontend: manual verification only (existing admin.js has no test suite) — click through post/void/error paths in the browser against `EZEE_MOCK=true`.
