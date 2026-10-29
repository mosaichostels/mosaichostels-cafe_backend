#!/usr/bin/env bash
# Promote one user to SuperAdmin (roles = ["ROLE_SUPERADMIN"]) and force a fresh login.
# Usage: MONGODB_URI=... scripts/promote-superadmin.sh <username>
# Safe to re-run. Rollout: backend with ROLE_SUPERADMIN live first, then this, then the user logs in again.
set -euo pipefail

if [ $# -ne 1 ] || [ -z "$1" ]; then
  echo "Usage: MONGODB_URI=... $0 <username>" >&2
  exit 2
fi
if [ -z "${MONGODB_URI:-}" ]; then
  echo "MONGODB_URI is not set" >&2
  exit 2
fi

# Username travels in the environment, never spliced into the JS. Collection: User.java @Document("users").
PROMOTE_USER="$1" mongosh "$MONGODB_URI" --quiet --eval '
const username = process.env.PROMOTE_USER;
const r = db.getCollection("users").updateOne(
  { username: username },
  { $set: { roles: ["ROLE_SUPERADMIN"], tokensValidFrom: Date.now() } });
if (r.matchedCount === 0) { print("No such user: " + username); quit(1); }
print("Promoted " + username + " -> roles [ROLE_SUPERADMIN]; they must log in again.");
'
