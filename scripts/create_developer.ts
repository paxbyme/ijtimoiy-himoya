import * as admin from "firebase-admin";
import * as path from "path";
import * as fs from "fs";

// ---------------------------------------------------------------------------
// Firebase initialisation
// ---------------------------------------------------------------------------

function initFirebase(): admin.app.App {
  const serviceAccountPath =
    process.env.FIREBASE_SERVICE_ACCOUNT_PATH ??
    path.resolve(__dirname, "..", "firebase-service-account.json");

  if (!fs.existsSync(serviceAccountPath)) {
    console.error(
      `Service account file not found at: ${serviceAccountPath}\n` +
        "Set FIREBASE_SERVICE_ACCOUNT_PATH or place the file at ../firebase-service-account.json"
    );
    process.exit(1);
  }

  const serviceAccount = JSON.parse(
    fs.readFileSync(serviceAccountPath, "utf-8")
  );

  return admin.initializeApp({
    credential: admin.credential.cert(serviceAccount as admin.ServiceAccount),
  });
}

// ---------------------------------------------------------------------------
// CLI argument parsing
// ---------------------------------------------------------------------------

interface CliArgs {
  phone: string;
  password: string;
  name: string;
}

function parseArgs(): CliArgs {
  const args = process.argv.slice(2);
  const map = new Map<string, string>();

  for (let i = 0; i < args.length; i += 2) {
    const key = args[i]?.replace(/^--/, "");
    const value = args[i + 1];
    if (key && value) {
      map.set(key, value);
    }
  }

  const phone = map.get("phone");
  const password = map.get("password");
  const name = map.get("name");

  if (!phone || !password || !name) {
    console.error(
      "Usage: npm run create-developer -- --phone <phone> --password <password> --name <name>"
    );
    process.exit(1);
  }

  return { phone, password, name };
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main(): Promise<void> {
  const { phone, password, name } = parseArgs();

  const app = initFirebase();
  const auth = admin.auth();
  const db = admin.firestore();

  const email = `${phone}@manager.local`;

  // 1. Create Firebase Auth user
  console.log(`Creating Auth user with email: ${email} ...`);
  const userRecord = await auth.createUser({
    email,
    password,
    displayName: name,
  });
  console.log(`Auth user created: ${userRecord.uid}`);

  // 2. Set custom claims (no departmentId — DEVELOPER is cross-department)
  await auth.setCustomUserClaims(userRecord.uid, {
    role: "DEVELOPER",
    departmentId: "",
  });

  // 3. Create user document in Firestore
  const userDoc = {
    uid: userRecord.uid,
    id: userRecord.uid,
    email,
    phone,
    name,
    role: "DEVELOPER",
    departmentId: "",
    isActive: true,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };

  await db.collection("users").doc(userRecord.uid).set(userDoc);
  console.log(`User document created in Firestore.`);

  // 4. Summary
  console.log("\n--- Developer Created Successfully ---");
  console.log(`  UID:      ${userRecord.uid}`);
  console.log(`  Email:    ${email}`);
  console.log(`  Name:     ${name}`);
  console.log(`  Phone:    ${phone}`);
  console.log(`  Role:     DEVELOPER`);
  console.log("--------------------------------------\n");
  console.log("Sign in at the web dashboard. This account cannot be used on the mobile app.");

  app.delete();
}

main().catch((err) => {
  console.error("Failed to create developer:", err);
  process.exit(1);
});
