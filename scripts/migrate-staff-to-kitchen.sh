#!/usr/bin/env bash
# Migrate staff accounts to kitchen: ROLE_STAFF / STAFF -> ROLE_KITCHEN in every user's roles (other roles kept, de-duplicated).
# Usage: MONGODB_URI=... scripts/migrate-staff-to-kitchen.sh [--dry-run]   (--dry-run changes nothing; prints counts only)
# Also drops the unused cleaning_assignments collection (its code was removed; nothing reads it). --dry-run only counts it. Take a backup first: a drop is irreversible.
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
const hit = ["ROLE_STAFF", "STAFF"];
users.find({ roles: { $type: "array", $in: hit } }, { roles: 1 }).forEach(u => {
  matched++;
  if (!dry) changed += users.updateOne({ _id: u._id, roles: u.roles }, { $set: { roles: rewrite(u.roles) } }).modifiedCount;
});
const skipped = users.countDocuments({ roles: { $not: { $type: "array" }, $in: hit } }); // non-array roles are never rewritten
print((dry ? "dry-run: matched " + matched + ", would modify " + matched : "matched " + matched + ", modified " + changed) + ", skipped non-array " + skipped);

// Collection left over from the removed cleaning-assignment feature.
if (db.getCollectionNames().includes("cleaning_assignments")) {
  const n = db.getCollection("cleaning_assignments").countDocuments();
  if (!dry) db.getCollection("cleaning_assignments").drop();
  print((dry ? "dry-run: would drop" : "dropped") + " cleaning_assignments (" + n + " documents)");
} else {
  print("cleaning_assignments: not present");
}
'
