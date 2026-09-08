/*
 * Priority Backlog Tracker — sample data loader
 * ---------------------------------------------
 * Loads a realistic demo backlog straight into MongoDB, without running the app.
 * Mirrors com.backlogtracker.demo.DemoDataSeeder.
 *
 * Usage:
 *   mongosh "<your connection string>/backlog" scripts/seed-demo.mongosh.js
 *
 * e.g. local:   mongosh "mongodb://localhost:27017/backlog" scripts/seed-demo.mongosh.js
 *      Atlas:   mongosh "mongodb+srv://user:pass@cluster/backlog" scripts/seed-demo.mongosh.js
 *
 * Safe to re-run: it removes only the rows it previously created (createdBy = "demo-script")
 * before re-inserting. It never touches items you created yourself.
 *
 * Login: sign in as  test123 / test123.  Start the app once first so it creates the
 * config document and the test123 account; this script reuses that account's password
 * hash for the three sample founders so they can log in too.
 */

const MARKER = "demo-script";
const now = Date.now();
const day = 864e5;
const at = (d) => new Date(now + d * day);

// --- config singleton (only if the app hasn't created it yet) ----------------------
if (!db.config.findOne({ _id: "app-config" })) {
  db.config.insertOne({
    _id: "app-config",
    priorityValues: { Critical: 4, High: 3, Medium: 2, Low: 1 },
    priorities: ["Critical", "High", "Medium", "Low"],
    priorityWeight: 0.333,
    urgencyWeight: 0.333,
    effortWeight: 0.334,
    urgencyWindowDays: 14,
    staleThresholdDays: 14,
    buriedThresholdDays: 30,
    buriedPriorityLevels: ["Low"],
    categories: ["Research", "Skill-Building", "Project", "Technical Discussion", "Admin-Ops", "Other"],
    defaultDueDateOffsetDays: 30,
    effortCapDays: 30,
  });
  print("config: seeded default app-config");
}

// --- users -----------------------------------------------------------------------
const seededUser = db.users.findOne({ email: "test123" });
const hash = seededUser
  ? seededUser.passwordHash
  : "$2a$10$7EqJtq98hPqEX7fNZaFWoOtDAG.qMdBB0k1i6R2n8gPz2yq0d0Zu."; // placeholder ("test123" not guaranteed)
if (!seededUser) {
  print("WARNING: test123 account not found — start the app once so logins work, then re-run.");
}

const founders = [
  { name: "Alex Rivera", email: "alex@demo.test", userCode: "ALX" },
  { name: "Priya Shah", email: "priya@demo.test", userCode: "PRY" },
  { name: "Sam Lee", email: "sam@demo.test", userCode: "SAM" },
];
const ownerId = {};
for (const f of founders) {
  db.users.updateOne(
    { email: f.email },
    {
      $set: { name: f.name, passwordHash: hash, role: "OWNER", userCode: f.userCode },
      $setOnInsert: { createdAt: new Date() },
    },
    { upsert: true },
  );
  ownerId[f.userCode] = db.users.findOne({ email: f.email })._id;
}
const testId = seededUser ? seededUser._id : null;

// --- wipe previous demo rows ----------------------------------------------------
db.items.deleteMany({ createdBy: MARKER });
db.archivedItems.deleteMany({ createdBy: MARKER });

// --- live items ---------------------------------------------------------------
const eff = (value, unit) => ({ value, unit });
const specs = [
  ["Fix signup 500 on duplicate email", "Project", "Critical", eff(45, "MINUTES"), -2, "IN_PROGRESS", "ALX", 6],
  ["SOC2 evidence collection kickoff", "Admin-Ops", "High", eff(3, "DAYS"), 9, "BACKLOG", "PRY", 12],
  ["Migrate CI to cheaper runners", "Technical Discussion", "Medium", eff(6, "HOURS"), 21, "BACKLOG", "SAM", 20],
  ["Draft Series A narrative deck", "Project", "High", eff(2, "DAYS"), 5, "IN_PROGRESS", "PRY", 8],
  ["Investigate churn spike in EU", "Research", "Critical", eff(8, "HOURS"), 1, "BACKLOG", "ALX", 4],
  ["Set up on-call rotation", "Admin-Ops", "Medium", eff(3, "HOURS"), 14, "BACKLOG", "SAM", 15],
  ["Rewrite onboarding checklist", "Skill-Building", "Low", eff(2, "HOURS"), 45, "BACKLOG", null, 38],
  ["Evaluate Postgres vs Mongo for events", "Technical Discussion", "Medium", eff(1, "DAYS"), 30, "BACKLOG", "SAM", 25],
  ["Clean up unused feature flags", "Project", "Low", eff(1, "HOURS"), 60, "BACKLOG", null, 41],
  ["Customer interview: Acme Corp", "Research", "High", eff(45, "MINUTES"), -6, "BACKLOG", "PRY", 10],
  ["Renew domain + TLS certs", "Admin-Ops", "High", eff(30, "MINUTES"), -20, "BACKLOG", "ALX", 22],
  ["Prototype usage-based pricing", "Project", "Medium", eff(4, "DAYS"), 25, "BACKLOG", "PRY", 7],
  ["Write runbook for prod restore", "Admin-Ops", "High", eff(4, "HOURS"), 12, "BACKLOG", "SAM", 9],
  ["Read 'Designing Data-Intensive Apps' ch.5-7", "Skill-Building", "Low", eff(5, "DAYS"), 90, "BACKLOG", null, 50],
  ["Reduce cold-start latency on Render", "Project", "Medium", eff(5, "HOURS"), 18, "IN_PROGRESS", "SAM", 11],
  ["Competitor teardown: Linear", "Research", "Low", eff(2, "HOURS"), 40, "BACKLOG", null, 36],
  ["Hire first support contractor", "Admin-Ops", "Critical", eff(2, "DAYS"), 3, "BACKLOG", "PRY", 5],
  ["Add audit log to settings changes", "Project", "Medium", eff(6, "HOURS"), -1, "BACKLOG", "ALX", 17],
  ["Spike: WebSocket vs SSE for live board", "Technical Discussion", "Low", eff(3, "HOURS"), 55, "BACKLOG", null, 33],
  ["Quarterly board update doc", "Admin-Ops", "High", eff(1, "DAYS"), 7, "BACKLOG", "PRY", 6],
];

let n = 0;
const itemDocs = specs.map(([title, category, priority, effortEstimate, dueIn, status, code, createdDaysAgo]) => {
  n += 1;
  return {
    itemId: "ITM-" + String(n).padStart(3, "0"),
    title, category, priority, effortEstimate,
    dueDate: at(dueIn),
    status, scope: "SHARED",
    createdBy: MARKER, lastUpdatedBy: testId || MARKER,
    ownerId: code ? ownerId[code] : null,
    notes: null,
    createdAt: at(-createdDaysAgo),
    updatedAt: new Date(),
  };
});
db.items.insertMany(itemDocs);

// --- archived items ---------------------------------------------------------
const archivedSpecs = [
  ["Kill the legacy cron worker", "Project", "Medium", eff(4, "HOURS"), "RESOLVED", "SAM", 20, 4],
  ["Evaluate Segment for analytics", "Technical Discussion", "Low", eff(2, "HOURS"), "REJECTED", "PRY", 30, 9],
  ["One-off data backfill for beta users", "Admin-Ops", "High", eff(1, "DAYS"), "RESOLVED", "ALX", 14, 2],
  ["Explore native mobile app", "Research", "Low", eff(3, "DAYS"), "ARCHIVED", null, 45, 15],
];
const archivedDocs = archivedSpecs.map(([title, category, priority, effortEstimate, terminalStatus, code, createdDaysAgo, doneDaysAgo], i) => ({
  itemId: "ITM-" + String(specs.length + i + 1).padStart(3, "0"),
  title, category, priority, effortEstimate,
  dueDate: at(-(doneDaysAgo + 2)),
  scope: "SHARED",
  createdBy: MARKER, lastUpdatedBy: testId || MARKER,
  ownerId: code ? ownerId[code] : null,
  notes: null,
  createdAt: at(-createdDaysAgo),
  updatedAt: at(-doneDaysAgo),
  terminalStatus,
  completionDate: at(-doneDaysAgo),
  movedAt: at(-doneDaysAgo),
}));
db.archivedItems.insertMany(archivedDocs);

// --- keep the shared ITM- counter ahead of the demo range -----------------
db.counters.updateOne(
  { _id: "shared" },
  { $max: { seq: specs.length + archivedSpecs.length } },
  { upsert: true },
);

print("");
print("Seeded: " + db.items.countDocuments({ createdBy: MARKER }) + " live items, " +
  db.archivedItems.countDocuments({ createdBy: MARKER }) + " archived, " +
  db.users.countDocuments({}) + " users total.");
print("Sign in as  test123 / test123");
