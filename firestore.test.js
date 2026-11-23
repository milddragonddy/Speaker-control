const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_nurse_123";
const BOB_UID = "bob_patient_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write complaints or noiseLogs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("complaints").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("noiseLogs").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("complaints")
      .doc("comp_1")
      .set({
        userId: ALICE_UID,
        reporterName: "Alice",
        facilityName: "General Hospital Ward 2",
        sourceType: "LOUDSPEAKER",
        measuredDb: 78,
        peakDb: 86,
        severity: "CRITICAL",
        status: "SUBMITTED",
        description: "Loudspeaker outside ICU gate",
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});

test("Authenticated owner: valid Kotlin complaint create, list, partial update, and terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid create payload mirroring NoiseRepository.createComplaint (no `id` key, omits null authorityResponse)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("complaints")
      .doc("comp_1")
      .set({
        userId: ALICE_UID,
        reporterName: "Dr. Aye",
        facilityName: "Yangon General Ward 3",
        sourceType: "LOUDSPEAKER",
        measuredDb: 76,
        peakDb: 88,
        severity: "CRITICAL",
        status: "SUBMITTED",
        description: "External loudspeaker exceeding 80 dB near cardiac ward.",
        createdAt: created,
        updatedAt: created,
      })
  );

  // List matches repository query users/{uid}/complaints
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("complaints").get()
  );

  // Valid partial update (status escalated + authorityResponse)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("complaints")
      .doc("comp_1")
      .update({
        status: "ESCALATED",
        authorityResponse: "Sent to Township Health & Municipal Office",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("complaints")
      .doc("comp_1")
      .update({
        createdAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Reject ghost field / @DocumentId `id` field in payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("complaints")
      .doc("comp_2")
      .set({
        id: "comp_2",
        userId: ALICE_UID,
        reporterName: "Dr. Aye",
        facilityName: "Clinic",
        sourceType: "LOUDSPEAKER",
        measuredDb: 70,
        peakDb: 75,
        severity: "HIGH",
        status: "SUBMITTED",
        description: "Test",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("complaints")
      .doc("comp_future")
      .set({
        userId: ALICE_UID,
        reporterName: "Dr. Aye",
        facilityName: "Clinic",
        sourceType: "LOUDSPEAKER",
        measuredDb: 70,
        peakDb: 75,
        severity: "HIGH",
        status: "SUBMITTED",
        description: "Future ts",
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Transition to RESOLVED, then verify terminal lock prevents further updates
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("complaints")
      .doc("comp_1")
      .update({
        status: "RESOLVED",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("complaints")
      .doc("comp_1")
      .update({
        status: "ESCALATED",
        updatedAt: new Date(Date.now() - 200),
      })
  );
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's complaints or noiseLogs", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("complaints")
      .doc("alice_comp")
      .set({
        userId: ALICE_UID,
        reporterName: "Alice",
        facilityName: "ICU Ward",
        sourceType: "LOUDSPEAKER",
        measuredDb: 72,
        peakDb: 82,
        severity: "HIGH",
        status: "SUBMITTED",
        description: "Loud music near ICU",
        createdAt: new Date(Date.now() - 5000),
        updatedAt: new Date(Date.now() - 5000),
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("complaints").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("complaints")
      .doc("alice_comp")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("complaints")
      .doc("alice_comp")
      .update({
        status: "RESOLVED",
        updatedAt: new Date(Date.now() - 1000),
      })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("complaints")
      .doc("alice_comp")
      .delete()
  );
});

test("NoiseLog create, immutability on update, and validation bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("noiseLogs")
      .doc("log_1")
      .set({
        userId: ALICE_UID,
        zoneName: "ICU Quiet Zone",
        avgDb: 68,
        peakDb: 79,
        thresholdDb: 45,
        autoGuarded: true,
        createdAt: now,
      })
  );

  // NoiseLogs are immutable evidence records
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("noiseLogs")
      .doc("log_1")
      .update({ avgDb: 30 })
  );

  // Out-of-bounds dB rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("noiseLogs")
      .doc("log_bad")
      .set({
        userId: ALICE_UID,
        zoneName: "ICU Quiet Zone",
        avgDb: 250,
        peakDb: 300,
        thresholdDb: 45,
        autoGuarded: false,
        createdAt: now,
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("noiseLogs").get()
  );
});
