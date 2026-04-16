/**
 * fix_staff_department.ts
 *
 * Fixes staff documents that were created with an empty departmentId.
 * This happens when the manager's JWT token was stale during staff creation.
 *
 * Usage:
 *   npm run fix-staff-dept -- --managerId <UID>
 */

import * as admin from "firebase-admin";
import * as path from "path";
import * as fs from "fs";

function initFirebase(): admin.app.App {
  const serviceAccountPath =
    process.env.FIREBASE_SERVICE_ACCOUNT_PATH ??
    path.resolve(__dirname, "..", "firebase-service-account.json");

  if (!fs.existsSync(serviceAccountPath)) {
    console.error(`Service account file not found at: ${serviceAccountPath}`);
    process.exit(1);
  }

  const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, "utf-8"));
  return admin.initializeApp({
    credential: admin.credential.cert(serviceAccount as admin.ServiceAccount),
  });
}

async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const managerIdIndex = args.indexOf("--managerId");
  if (managerIdIndex === -1 || !args[managerIdIndex + 1]) {
    console.error("Usage: npm run fix-staff-dept -- --managerId <UID>");
    process.exit(1);
  }
  const managerId = args[managerIdIndex + 1];

  const app = initFirebase();
  const db = admin.firestore();
  const auth = admin.auth();

  // 1. Get the manager's departmentId from Firestore
  const managerDoc = await db.collection("users").doc(managerId).get();
  if (!managerDoc.exists) {
    console.error(`Manager ${managerId} not found in Firestore`);
    process.exit(1);
  }
  const managerData = managerDoc.data()!;
  const departmentId = managerData.departmentId as string;

  if (!departmentId) {
    console.error(`Manager ${managerId} has no departmentId set in Firestore`);
    process.exit(1);
  }
  console.log(`Manager departmentId: ${departmentId}`);

  // 2. Find staff created by this manager with empty departmentId
  const brokenStaff = await db
    .collection("users")
    .where("managerId", "==", managerId)
    .where("role", "==", "STAFF")
    .get();

  const toFix = brokenStaff.docs.filter(
    (doc) => !doc.data().departmentId
  );

  if (toFix.length === 0) {
    console.log("No staff with missing departmentId found. Nothing to fix.");
    app.delete();
    return;
  }

  console.log(`Found ${toFix.length} staff with empty departmentId. Fixing...`);

  for (const doc of toFix) {
    const data = doc.data();
    console.log(`  Fixing staff: ${data.name} (${doc.id})`);

    // Update Firestore document
    await db.collection("users").doc(doc.id).update({ departmentId });

    // Update Firebase Auth custom claims
    await auth.setCustomUserClaims(doc.id, {
      role: "STAFF",
      departmentId,
    });

    console.log(`  ✓ Updated ${data.name}`);
  }

  console.log(`\nDone. Fixed ${toFix.length} staff document(s).`);
  app.delete();
}

main().catch((err) => {
  console.error("Failed:", err);
  process.exit(1);
});
