#!/usr/bin/env bash
# Migrate staff accounts to kitchen: ROLE_STAFF / STAFF -> ROLE_KITCHEN in every user's roles (other roles kept, de-duplicated).
# Usage: MONGODB_URI=... scripts/migrate-staff-to-kitchen.sh [--dry-run]   (--dry-run changes nothing; prints counts only)
# Idempotent. Does not touch tokensValidFrom: roles load from the DB per request, so sessions survive. Prints counts, never usernames.
# Reverse (one line): swap ROLE_KITCHEN for ROLE_STAFF in the rewrite() function below and re-run; note it also flips accounts that were KITCHEN before.
set -euo pipefail

DRY=0
if [ $# -gt 1 ] || { [ $# -eq 1 ] && [ "$1" != "--dry-run" ]; }; then
  echo "Usage: MONGODB_URI=... $0 [--dry-run]" >&2
  exit 2
fi
[ "${1:-}" = "--dry-run" ] && DRY=1
if [ -z "${MONGODB_URI:-}" ]; then
  echo "MONGODB_URI is not set" >&2
  exit 2
fi

# Collection: User.java @Document("users").
DRY_RUN="$DRY" mongosh "$MONGODB_URI" --quiet --eval '
// BEGIN rewrite
function rewrite(roles) {
  const out = [];
  for (const r of roles) {
    const n = (r === "ROLE_STAFF" || r === "STAFF") ? "ROLE_KITCHEN" : r;
    if (!out.includes(n)) out.push(n);
  }
  return out;
}
// END rewrite
const dry = process.env.DRY_RUN === "1";
const users = db.getCollection("users");
let matched = 0, changed = 0;
users.find({ roles: { $in: ["ROLE_STAFF", "STAFF"] } }, { roles: 1 }).forEach(u => {
  matched++;
  if (!dry) { users.updateOne({ _id: u._id }, { $set: { roles: rewrite(u.roles) } }); changed++; }
});
print((dry ? "dry-run: matched " + matched + ", would modify " + matched : "matched " + matched + ", modified " + changed));
'
