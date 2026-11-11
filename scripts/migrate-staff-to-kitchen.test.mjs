// Run: node scripts/migrate-staff-to-kitchen.test.mjs  (checks rewrite() extracted from the script)
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
const src = readFileSync(new URL("./migrate-staff-to-kitchen.sh", import.meta.url), "utf8");
const rewrite = new Function(src.match(/\/\/ BEGIN rewrite\n([\s\S]*?)\/\/ END rewrite/)[1] + "return rewrite;")();
const e = (x, y) => assert.deepEqual(rewrite(x), y);
e(["ROLE_STAFF"], ["ROLE_KITCHEN"]);
e(["STAFF"], ["ROLE_KITCHEN"]);
e(["ROLE_STAFF", "ROLE_KITCHEN"], ["ROLE_KITCHEN"]);
e(["ROLE_ADMIN", "ROLE_STAFF"], ["ROLE_ADMIN", "ROLE_KITCHEN"]);
e(["ROLE_ADMIN"], ["ROLE_ADMIN"]);
e(rewrite(["ROLE_STAFF"]), ["ROLE_KITCHEN"]);
console.log("rewrite ok");
