const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

function validProjectPayload(uid) {
  return {
    userId: uid,
    title: "Valorant Finals Highlight Stream",
    sourceFileName: "valorant_finals.mp4",
    sourceUri: "content://media/external/video/media/101",
    durationSec: 1800,
    width: 1920,
    height: 1080,
    transcriptText: "[00:12] Incredible clutch play on site B!",
    campaignId: "camp_1",
    status: "READY",
    clipCount: 0,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

function validCampaignPayload(uid) {
  return {
    userId: uid,
    name: "Whop Gaming Creators Bounty",
    brandName: "ApexGear",
    payoutPer1kViews: "$1.75 / 1K views",
    targetPlatform: "TIKTOK",
    minDurationSec: 15,
    maxDurationSec: 60,
    logoPlacement: "TOP_RIGHT",
    requiredLogoAsset: "apexgear_watermark_v2.png",
    safeZoneTopPct: 12,
    safeZoneBottomPct: 22,
    requiredHashtags: "#whop #gaming #clutch",
    mandatoryCaptionCta: "Link in bio for ApexGear",
    seoGuidance: "Include game name and clutch reaction in first 40 chars",
    minConfidenceScore: 80,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

function validClipPayload(uid) {
  return {
    userId: uid,
    projectId: "proj_1",
    campaignId: "camp_1",
    title: "Insane 1v4 Clutch in Overtime!",
    category: "CLUTCH_PLAY",
    reasonForSelection: "Peak vocal excitement + rapid elimination sequence.",
    confidenceScore: 94,
    startSec: 120,
    endSec: 152,
    aspectRatio: "9:16",
    reframeFocusX: 50,
    subtitlesEnabled: true,
    subtitleText: "NO WAY HE HITS THAT SHOT!",
    captionText: "Insane 1v4 clutch! Link in bio for ApexGear #whop #gaming #clutch",
    hashtags: "#whop #gaming #clutch",
    seoTitle: "Valorant 1v4 Overtime Clutch Reaction",
    seoKeywords: "valorant, clutch, gaming highlights, whop",
    logoPlacement: "TOP_RIGHT",
    reviewStatus: "PROPOSED",
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

test("1. Unauthenticated user cannot read or write projects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .set(validProjectPayload(ALICE_UID))
  );
});

test("2. Authenticated owner can create, read, list, and update own project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("projects")
    .doc("proj_1");

  await assertSucceeds(docRef.set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .where("userId", "==", ALICE_UID)
      .get()
  );
  await assertSucceeds(
    docRef.update({
      status: "CLIPS_GENERATED",
      clipCount: 4,
      updatedAt: new Date(),
    })
  );
});

test("3. Cross-user access is strictly rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .set(validProjectPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .get()
  );
});

test("4. Shadow field injection and invalid enum values are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badProject = { ...validProjectPayload(ALICE_UID), isAdmin: true };
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_bad")
      .set(badProject)
  );

  const badClip = { ...validClipPayload(ALICE_UID), reviewStatus: "HACKED" };
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("clips")
      .doc("clip_bad")
      .set(badClip)
  );
});

test("5. WhopCampaign and ClipCandidate valid writes succeed and invalid durations fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("campaigns")
      .doc("camp_1")
      .set(validCampaignPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("clips")
      .doc("clip_1")
      .set(validClipPayload(ALICE_UID))
  );

  const invertedClip = { ...validClipPayload(ALICE_UID), startSec: 200, endSec: 150 };
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("clips")
      .doc("clip_inverted")
      .set(invertedClip)
  );
});

test("6. ApiUsageLog allows create but forbids update (append-only telemetry)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("api_logs")
    .doc("log_1");

  await assertSucceeds(
    logRef.set({
      userId: ALICE_UID,
      providerId: "firebase_ai_primary",
      modelName: "gemini-3.8-flash",
      operation: "detect_highlights",
      latencyMs: 1420,
      estimatedTokens: 890,
      retryCount: 0,
      success: true,
      errorMessage: "",
      createdAt: new Date(),
    })
  );

  await assertFails(logRef.update({ latencyMs: 10 }));
});
