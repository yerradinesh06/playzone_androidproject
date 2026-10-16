/**
 * PS 22 Tournament Manager - Firebase Custom Claims Script
 *
 * Usage:
 * 1. npm install firebase-admin
 * 2. Export service account key: export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"
 * 3. Run: node set_custom_claims.js <uid> <role> [teamId]
 *
 * Roles: ORGANISER, TEAM_MANAGER, SPECTATOR
 * Example:
 * node set_custom_claims.js "usr_12345" "ORGANISER"
 * node set_custom_claims.js "usr_67890" "TEAM_MANAGER" "TEAM-HBR"
 */

const admin = require("firebase-admin");

if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
  console.error("Error: Please set GOOGLE_APPLICATION_CREDENTIALS environment variable.");
  process.exit(1);
}

admin.initializeApp({
  credential: admin.credential.applicationDefault()
});

const args = process.argv.slice(2);
const uid = args[0];
const role = args[1];
const teamId = args[2] || null;

if (!uid || !role) {
  console.log("Usage: node set_custom_claims.js <uid> <role> [teamId]");
  console.log("Roles: ORGANISER, TEAM_MANAGER, SPECTATOR");
  process.exit(1);
}

if (!["ORGANISER", "TEAM_MANAGER", "SPECTATOR"].includes(role)) {
  console.error("Error: Role must be ORGANISER, TEAM_MANAGER, or SPECTATOR.");
  process.exit(1);
}

if (role === "TEAM_MANAGER" && !teamId) {
  console.error("Error: TEAM_MANAGER role requires a teamId.");
  process.exit(1);
}

async function setClaims() {
  const claims = {
    ps22_role: role,
    ...(teamId ? { ps22_team_id: teamId } : {})
  };

  await admin.auth().setCustomUserClaims(uid, claims);
  console.log(`Successfully assigned custom claims to UID ${uid}:`, claims);
  process.exit(0);
}

setClaims().catch((err) => {
  console.error("Error setting custom claims:", err);
  process.exit(1);
});
