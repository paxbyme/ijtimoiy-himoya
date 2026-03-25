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

function parseArgs(): { managerId: string } {
  const args = process.argv.slice(2);
  const map = new Map<string, string>();

  for (let i = 0; i < args.length; i += 2) {
    const key = args[i]?.replace(/^--/, "");
    const value = args[i + 1];
    if (key && value) {
      map.set(key, value);
    }
  }

  const managerId = map.get("managerId");
  if (!managerId) {
    console.error("Usage: npm run seed-data -- --managerId <MANAGER_UID>");
    process.exit(1);
  }

  return { managerId };
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function randomElement<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}

function randomInt(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function pastDate(daysAgo: number): Date {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  return d;
}

function futureDate(daysAhead: number): Date {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  return d;
}

// ---------------------------------------------------------------------------
// Sample data definitions
// ---------------------------------------------------------------------------

const STAFF_TEMPLATES = [
  { name: "Alice Johnson", phone: "5550001001" },
  { name: "Bob Williams", phone: "5550001002" },
  { name: "Carol Martinez", phone: "5550001003" },
  { name: "David Lee", phone: "5550001004" },
  { name: "Eva Chen", phone: "5550001005" },
];

const TASK_TITLES = [
  "Complete quarterly report",
  "Update project documentation",
  "Fix login page bug",
  "Review pull requests",
  "Set up CI/CD pipeline",
  "Prepare sprint demo",
  "Conduct code review",
  "Write unit tests for auth module",
  "Design database schema",
  "Implement push notifications",
  "Optimize API response times",
  "Migrate legacy endpoints",
  "Create onboarding guide",
  "Audit security permissions",
  "Refactor payment module",
];

const TASK_STATUSES = ["PENDING", "IN_PROGRESS", "COMPLETED", "OVERDUE"] as const;
const TASK_PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const;

const AI_RULES = [
  {
    name: "Late Check-in Alert",
    description:
      "Trigger an alert when a staff member checks in more than 15 minutes late for 3 consecutive days.",
    condition: "consecutive_late_checkins >= 3 AND late_minutes > 15",
    action: "NOTIFY_MANAGER",
    isActive: true,
  },
  {
    name: "Low KPI Warning",
    description:
      "Flag staff members whose average KPI score drops below 60 for two consecutive review periods.",
    condition: "avg_kpi < 60 AND consecutive_low_periods >= 2",
    action: "FLAG_FOR_REVIEW",
    isActive: true,
  },
  {
    name: "High Performer Recognition",
    description:
      "Automatically nominate staff members with KPI scores above 90 for recognition.",
    condition: "avg_kpi >= 90",
    action: "NOMINATE_RECOGNITION",
    isActive: false,
  },
];

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main(): Promise<void> {
  const { managerId } = parseArgs();

  const app = initFirebase();
  const auth = admin.auth();
  const db = admin.firestore();

  // Look up manager to get departmentId
  const managerDoc = await db.collection("users").doc(managerId).get();
  if (!managerDoc.exists) {
    console.error(`Manager with UID "${managerId}" not found in Firestore.`);
    process.exit(1);
  }

  const managerData = managerDoc.data()!;
  const departmentId = managerData.departmentId as string;
  if (!departmentId) {
    console.error("Manager document is missing departmentId.");
    process.exit(1);
  }

  console.log(
    `Seeding data for manager "${managerData.name}" in department ${departmentId}\n`
  );

  // ------------------------------------------------------------------
  // 1. Create sample staff users (pick 4 from templates)
  // ------------------------------------------------------------------
  const staffCount = 4;
  const staffUids: string[] = [];

  console.log(`Creating ${staffCount} staff users ...`);
  for (let i = 0; i < staffCount; i++) {
    const template = STAFF_TEMPLATES[i];
    const email = `${template.phone}@manager.local`;

    const userRecord = await auth.createUser({
      email,
      password: "StaffPass123",
      displayName: template.name,
    });

    await auth.setCustomUserClaims(userRecord.uid, {
      role: "STAFF",
      departmentId,
    });

    await db
      .collection("users")
      .doc(userRecord.uid)
      .set({
        uid: userRecord.uid,
        email,
        phone: template.phone,
        name: template.name,
        role: "STAFF",
        departmentId,
        managerId,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

    staffUids.push(userRecord.uid);
    console.log(`  [+] ${template.name} (${userRecord.uid})`);
  }

  // ------------------------------------------------------------------
  // 2. Create sample tasks (12 tasks)
  // ------------------------------------------------------------------
  const taskCount = 12;
  console.log(`\nCreating ${taskCount} tasks ...`);

  for (let i = 0; i < taskCount; i++) {
    const title = TASK_TITLES[i % TASK_TITLES.length];
    const assignedTo = randomElement(staffUids);
    const status = randomElement([...TASK_STATUSES]);
    const priority = randomElement([...TASK_PRIORITIES]);
    const createdDaysAgo = randomInt(1, 30);
    const dueDaysFromNow = randomInt(-5, 14); // negative = overdue

    const taskRef = db.collection("tasks").doc();
    await taskRef.set({
      id: taskRef.id,
      title,
      description: `Auto-generated task: ${title}`,
      status,
      priority,
      assignedTo,
      departmentId,
      createdBy: managerId,
      createdAt: admin.firestore.Timestamp.fromDate(pastDate(createdDaysAgo)),
      dueDate: admin.firestore.Timestamp.fromDate(
        dueDaysFromNow >= 0
          ? futureDate(dueDaysFromNow)
          : pastDate(Math.abs(dueDaysFromNow))
      ),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    console.log(
      `  [+] "${title}" -> ${assignedTo.slice(0, 8)}... [${status}/${priority}]`
    );
  }

  // ------------------------------------------------------------------
  // 3. Create KPI scores for each staff member
  // ------------------------------------------------------------------
  console.log(`\nCreating KPI scores ...`);

  const kpiCategories = [
    "Productivity",
    "Quality",
    "Communication",
    "Punctuality",
  ];

  for (const staffUid of staffUids) {
    for (const category of kpiCategories) {
      const score = randomInt(50, 100);
      const kpiRef = db.collection("kpiScores").doc();
      await kpiRef.set({
        id: kpiRef.id,
        staffId: staffUid,
        departmentId,
        category,
        score,
        period: "2026-Q1",
        evaluatedBy: managerId,
        notes: `Auto-generated ${category} score.`,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }
    console.log(`  [+] 4 KPI scores for ${staffUid.slice(0, 8)}...`);
  }

  // ------------------------------------------------------------------
  // 4. Create AI rules for the department
  // ------------------------------------------------------------------
  console.log(`\nCreating AI rules ...`);

  for (const rule of AI_RULES) {
    const ruleRef = db.collection("aiRules").doc();
    await ruleRef.set({
      id: ruleRef.id,
      departmentId,
      name: rule.name,
      description: rule.description,
      condition: rule.condition,
      action: rule.action,
      isActive: rule.isActive,
      createdBy: managerId,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    console.log(`  [+] Rule: "${rule.name}" (active: ${rule.isActive})`);
  }

  // ------------------------------------------------------------------
  // Summary
  // ------------------------------------------------------------------
  console.log("\n========== Seed Summary ==========");
  console.log(`  Staff users created:  ${staffCount}`);
  console.log(`  Tasks created:        ${taskCount}`);
  console.log(`  KPI scores created:   ${staffCount * kpiCategories.length}`);
  console.log(`  AI rules created:     ${AI_RULES.length}`);
  console.log(`  Department:           ${departmentId}`);
  console.log(`  Manager:              ${managerId}`);
  console.log("==================================\n");

  app.delete();
}

main().catch((err) => {
  console.error("Failed to seed data:", err);
  process.exit(1);
});
