/**
 * Creates a manager account using Firebase Auth Admin SDK (HTTPS)
 * and Firestore REST API (HTTPS) — avoids gRPC entirely.
 */
import * as admin from "firebase-admin";
import * as fs from "fs";
import * as path from "path";
import { GoogleAuth } from "google-auth-library";

const SERVICE_ACCOUNT_PATH =
  process.env.FIREBASE_SERVICE_ACCOUNT_PATH ??
  path.resolve(__dirname, "..", "backend", "firebase-service-account.json");

const PHONE = process.env.ACCOUNT_PHONE ?? "8888888888";
const PASSWORD = process.env.ACCOUNT_PASSWORD ?? "Staff1234!";
const NAME = process.env.ACCOUNT_NAME ?? "Test User";
const DEPARTMENT = process.env.ACCOUNT_DEPARTMENT ?? "General";

async function getAccessToken(): Promise<string> {
  const auth = new GoogleAuth({
    keyFile: SERVICE_ACCOUNT_PATH,
    scopes: ["https://www.googleapis.com/auth/datastore"],
  });
  const client = await auth.getClient();
  const tokenResponse = await client.getAccessToken();
  if (!tokenResponse.token) throw new Error("Failed to get access token");
  return tokenResponse.token;
}

async function firestoreSet(
  projectId: string,
  accessToken: string,
  collection: string,
  docId: string,
  fields: Record<string, unknown>
): Promise<void> {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${collection}/${docId}`;

  // Convert JS object to Firestore REST API field format
  function toFirestoreValue(val: unknown): Record<string, unknown> {
    if (val === null || val === undefined) return { nullValue: null };
    if (typeof val === "string") return { stringValue: val };
    if (typeof val === "boolean") return { booleanValue: val };
    if (typeof val === "number") return { integerValue: String(val) };
    if (typeof val === "object" && !Array.isArray(val)) {
      const mapFields: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(val as Record<string, unknown>)) {
        mapFields[k] = toFirestoreValue(v);
      }
      return { mapValue: { fields: mapFields } };
    }
    return { stringValue: String(val) };
  }

  const firestoreFields: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(fields)) {
    if (key === "createdAt" || key === "updatedAt") {
      firestoreFields[key] = { timestampValue: new Date().toISOString() };
    } else {
      firestoreFields[key] = toFirestoreValue(value);
    }
  }

  const response = await fetch(url, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ fields: firestoreFields }),
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Firestore REST error ${response.status}: ${text}`);
  }
}

async function deleteOrphanedUser(auth: admin.auth.Auth, uid: string) {
  try {
    await auth.deleteUser(uid);
    console.log(`Deleted orphaned user: ${uid}`);
  } catch {
    // doesn't exist, ignore
  }
}

async function main() {
  const serviceAccount = JSON.parse(fs.readFileSync(SERVICE_ACCOUNT_PATH, "utf-8"));
  const projectId: string = serviceAccount.project_id;

  // Init Firebase Admin (Auth only — no Firestore SDK)
  const app = admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
  });
  const auth = admin.auth();

  // Clean up previous orphaned user
  await deleteOrphanedUser(auth, "x4JXpBete8ZxQjzrJLzj3GuemUv2");

  const email = `${PHONE}@manager.local`;

  // 1. Create Firebase Auth user
  console.log(`Creating Auth user: ${email} ...`);
  const userRecord = await auth.createUser({ email, password: PASSWORD, displayName: NAME });
  console.log(`Auth user created: ${userRecord.uid}`);

  // 2. Get Firestore access token (REST)
  console.log("Getting Firestore access token...");
  const accessToken = await getAccessToken();

  // 3. Create department document
  const departmentId = `dept_${Date.now()}`;
  console.log(`Creating department: ${departmentId} ...`);
  await firestoreSet(projectId, accessToken, "departments", departmentId, {
    id: departmentId,
    name: DEPARTMENT,
    managerId: userRecord.uid,
    createdAt: "now",
    updatedAt: "now",
  });
  console.log("Department created.");

  // 4. Set custom claims
  await auth.setCustomUserClaims(userRecord.uid, {
    role: "MANAGER",
    departmentId,
  });
  console.log("Custom claims set.");

  // 5. Create user document
  await firestoreSet(projectId, accessToken, "users", userRecord.uid, {
    uid: userRecord.uid,
    email,
    phone: PHONE,
    name: NAME,
    role: "MANAGER",
    departmentId,
    isActive: true,
    createdAt: "now",
    updatedAt: "now",
  });
  console.log("User document created.");

  console.log("\n--- Account Created Successfully ---");
  console.log(`  Phone:      ${PHONE}`);
  console.log(`  Password:   ${PASSWORD}`);
  console.log(`  Name:       ${NAME}`);
  console.log(`  Role:       MANAGER`);
  console.log(`  UID:        ${userRecord.uid}`);
  console.log(`  Department: ${DEPARTMENT} (${departmentId})`);
  console.log("------------------------------------\n");

  await app.delete();
}

main().catch((err) => {
  console.error("Failed:", err.message ?? err);
  process.exit(1);
});
