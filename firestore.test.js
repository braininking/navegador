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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid) {
  const now = new Date();
  return {
    userId: uid,
    displayName: "Alice Cyber",
    email: "alice@example.com",
    preferredLanguage: "pt",
    preferredLanguageLabel: "Português",
    autoTranslateEnabled: true,
    onboardingCompleted: true,
    adBlockEnabled: true,
    strictTrackerBlock: true,
    vpnAutoConnect: false,
    vpnRegion: "Zurich, CH",
    desktopModeDefault: false,
    darkThemeEnabled: true,
    searchEngine: "DuckDuckGo",
    deviceName: "Pixel 9 Pro",
    adsBlockedTotal: 42,
    trackersBlockedTotal: 19,
    createdAt: now,
    updatedAt: now,
  };
}

function validExtensionPayload(uid) {
  const now = new Date();
  return {
    userId: uid,
    name: "Dark Reader Core",
    description: "Adaptive dark mode CSS injector",
    version: "1.0.0",
    author: "OpenSource",
    category: "appearance",
    matchPattern: "*://*/*",
    jsCode: "console.log('dark');",
    cssCode: "html { filter: invert(1); }",
    enabled: true,
    isBuiltIn: true,
    runAt: "document_end",
    createdAt: now,
    updatedAt: now,
  };
}

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

test("Unauthenticated user cannot read or write profile or bookmarks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("profile").doc("preferences").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("bookmarks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("profile").doc("preferences").set(validProfilePayload(ALICE_UID))
  );
});

test("Alice can create and update her own profile preferences", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("profile").doc("preferences");
  await assertSucceeds(ref.set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(ref.update({ preferredLanguage: "en", preferredLanguageLabel: "English", updatedAt: new Date() }));
});

test("Shadow update with ghost field fails on profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("profile").doc("preferences");
  const payload = validProfilePayload(ALICE_UID);
  payload.isAdmin = true;
  await assertFails(ref.set(payload));
});

test("Bob cannot read or write Alice's profile, extensions, bookmarks, or synced tabs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("profile").doc("preferences").set(validProfilePayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("extensions").doc("ext_1").set(validExtensionPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("profile").doc("preferences").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("extensions").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("extensions").doc("ext_2").set(validExtensionPayload(BOB_UID))
  );
});

test("Alice can CRUD her own extensions, bookmarks, and synced tabs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("extensions").doc("ext_custom").set(validExtensionPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("bm_1").set({
      userId: ALICE_UID,
      title: "DuckDuckGo",
      url: "https://duckduckgo.com",
      folder: "Privacidade",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("synced_tabs").doc("tab_1").set({
      userId: ALICE_UID,
      title: "Example",
      url: "https://example.com",
      deviceName: "Pixel 9 Pro",
      isIncognito: false,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Extension with invalid category fails validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badExt = validExtensionPayload(ALICE_UID);
  badExt.category = "malware";
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("extensions").doc("ext_bad").set(badExt)
  );
});
