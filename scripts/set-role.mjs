#!/usr/bin/env node
/**
 * Provision an officer/admin account: sets the `role` custom claim used by the
 * security rules and the app, and writes the profile to /officers/{uid}.
 *
 *   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
 *   export FIREBASE_DATABASE_URL=https://<project>-default-rtdb.<region>.firebasedatabase.app
 *   node scripts/set-role.mjs priya@tnpolice.gov.in officer "Insp. Priya Raman" TNP-4821 "Adyar Traffic"
 *
 * The officer must sign out and back in (or wait for token refresh) to pick up the claim.
 */
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getDatabase } from 'firebase-admin/database';

const [email, role, name, badgeNumber, station = ''] = process.argv.slice(2);

if (!email || !['officer', 'admin'].includes(role) || !name || !badgeNumber) {
  console.error('Usage: node scripts/set-role.mjs <email> <officer|admin> <name> <badgeNumber> [station]');
  process.exit(1);
}
if (!process.env.FIREBASE_DATABASE_URL) {
  console.error('FIREBASE_DATABASE_URL is not set');
  process.exit(1);
}

initializeApp({ credential: applicationDefault(), databaseURL: process.env.FIREBASE_DATABASE_URL });

const user = await getAuth().getUserByEmail(email);
await getAuth().setCustomUserClaims(user.uid, { role });
await getDatabase().ref(`officers/${user.uid}`).set({ name, email, role, badgeNumber, station, active: true });

console.log(`✔ ${email} (${user.uid}) is now '${role}'`);
process.exit(0);
