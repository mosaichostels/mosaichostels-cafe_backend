---
name: ezee-integration
description: Rules and constraints for the eZee PMS charge-posting integration (src/main/java/com/hostel/ordering/ezee). Use before changing anything under ezee/, OrderService chargepost paths, or the ezee.* config keys.
user-invocable: false
---

# eZee PMS integration

This is the only money path in the codebase. A posted charge lands on a real
guest's folio and **cannot be undone through any API**. Read this before
editing `ezee/` or the chargepost branches of `OrderService`.

## Two different eZee APIs

| API | Endpoint key | Format | Used for |
|-----|--------------|--------|----------|
| POS2PMS | `ezee.endpoint` | XML | `roomquery`, `roomlist` — resolving a room to a folio/reservation |
| Kiosk Connectivity | `ezee.kiosk-endpoint` | JSON | `AddExtraCharge` — the actual charge post |

`EzeeClient` is the transport layer only: field maps in, field maps out. All
business logic belongs in `EzeeChargePostService` or its callers.

## The no-void constraint

POS2PMS has `voidcharge`. Kiosk Connectivity's `AddExtraCharge` does not.
`EzeeChargePostService.voidPost()` therefore never clears
`chargePostStatus` — it only writes an error string telling staff to remove
the charge line by hand in eZee PMS. Do not "fix" this by marking the order
voided; the charge is still live on the folio.

## Double-charge prevention

An order's items are split into two groups (`MENU`, `ESSENTIAL`), each
posting to its own pre-configured extra-charge item — so one mixed order
makes two `AddExtraCharge` calls. There is no cross-call rollback: the menu
group can succeed while the essential group fails.

`Order.chargePostedGroups` records which groups already posted. A retry
**must** skip those. Any change to the grouping, retry, or status logic has
to preserve this invariant:

> A group name present in `chargePostedGroups` is never posted again.

`post()` also early-returns when `chargePostStatus` is already `QUEUED`.

## Response parsing trap

eZee returns `Errors` (plural, `ErrorCode` 0) on success but `Error`
(singular) on rejection. `parseExtraChargeResponse` checks both. Reading only
one key makes every rejection parse as a success and silently lose charges.

## Error handling contract

`EzeeChargePostService.post()` never throws. Every failure path routes
through `markFailed()`, which sets `chargePostStatus=FAILED` and
`chargePostError`. Callers inspect the returned `Order`; they do not catch.

## Configuration

All values come from env vars, defaulting to blank (see
`application-prod.yml`):

- `EZEE_AUTH_CODE`, `EZEE_HOTEL_CODE` — credentials
- `EZEE_FOOD_CHARGE_ID` — extra-charge item for `MENU` items
- `EZEE_ESSENTIAL_CHARGE_ID` — extra-charge item for `ESSENTIAL` items
- `ezee.mock` — when true, `EzeeMockResponses` answers instead of the live
  PMS. Keep it `false` in prod; keep it `true` in every test.

A blank charge id is a configured-failure, not a crash: the group is skipped
and an error is collected.

## Room resolution

`roomquery` is already scoped to a room by the request field, so its rows
carry no per-row `room` tag — unlike `roomlist`. Do not filter rows by room.
If the room's occupants span more than one folio or reservation number, the
post fails rather than guessing which guest to bill.

## Testing

`EzeeChargePostServiceTest`, `EzeeClientTest`, `EzeeXmlUtilTest` cover this
package. Any change to grouping, retry, or response parsing needs a test that
fails if the charge is posted twice or a rejection is read as success.
