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

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("playlists").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("catalog_tracks").where("published", "==", true).get());
});

test("UserProfile: owner create/update succeeds; cross-user read/write and ghost field fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Stream",
      email: "alice@example.com",
      subscriptionTier: "free",
      preferredLanguage: "id",
      preferredTheme: "dark",
      audioQuality: "auto",
      notificationsEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Pro",
      subscriptionTier: "premium",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user PII read/write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("User subcollections: favorites, history, search_history, devices owner isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Favorites
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("favorites").doc("track_1").set({
      userId: ALICE_UID,
      trackId: "track_1",
      title: "Midnight Pulse",
      artistName: "Synth Wave",
      streamUrl: "https://api.audius.co/v1/tracks/track_1/stream",
      durationSeconds: 210,
      downloadable: true,
      source: "audius",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("favorites").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("favorites").get());

  // History create + update playCount
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("history").doc("track_1").set({
      userId: ALICE_UID,
      trackId: "track_1",
      title: "Midnight Pulse",
      artistName: "Synth Wave",
      streamUrl: "https://api.audius.co/v1/tracks/track_1/stream",
      durationSeconds: 210,
      downloadable: true,
      source: "audius",
      playCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("history").doc("track_1").update({
      playCount: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Search history
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("search_history").doc("q_1").set({
      userId: ALICE_UID,
      query: "synthwave",
      createdAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("search_history").get());

  // Devices
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("pixel_9").set({
      userId: ALICE_UID,
      deviceName: "Pixel 9 Pro",
      platform: "Android 16",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("devices").get());
});

test("Playlists & PlaylistTracks: public vs private isolation, parent owner check, and filtered queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Alice creates a private playlist
  await assertSucceeds(
    aliceDb.collection("playlists").doc("pl_priv").set({
      ownerId: ALICE_UID,
      ownerName: "Alice",
      name: "Late Night Focus",
      description: "Private focus mix",
      visibility: "private",
      trackCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice creates a public playlist
  await assertSucceeds(
    aliceDb.collection("playlists").doc("pl_pub").set({
      ownerId: ALICE_UID,
      ownerName: "Alice",
      name: "Neon Top Hits",
      visibility: "public",
      trackCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice adds track to her public playlist
  await assertSucceeds(
    aliceDb.collection("playlists").doc("pl_pub").collection("tracks").doc("tr_1").set({
      playlistId: "pl_pub",
      ownerId: ALICE_UID,
      visibility: "public",
      trackId: "tr_1",
      title: "Neon Horizon",
      artistName: "Nova",
      streamUrl: "https://api.audius.co/v1/tracks/tr_1/stream",
      durationSeconds: 185,
      downloadable: false,
      source: "audius",
      createdAt: now,
    })
  );

  // Bob can read Alice's public playlist and its tracks, but NOT private playlist
  await assertSucceeds(bobDb.collection("playlists").doc("pl_pub").get());
  await assertSucceeds(
    bobDb.collection("playlists").doc("pl_pub").collection("tracks").where("visibility", "==", "public").get()
  );
  await assertFails(bobDb.collection("playlists").doc("pl_priv").get());

  // Bob cannot modify Alice's public playlist or inject a track into it
  await assertFails(
    bobDb.collection("playlists").doc("pl_pub").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("playlists").doc("pl_pub").collection("tracks").doc("tr_2").set({
      playlistId: "pl_pub",
      ownerId: BOB_UID,
      visibility: "public",
      trackId: "tr_2",
      title: "Injected",
      artistName: "Bob",
      streamUrl: "https://api.audius.co/v1/tracks/tr_2/stream",
      durationSeconds: 100,
      downloadable: false,
      source: "audius",
      createdAt: now,
    })
  );

  // Filtered queries succeed; unfiltered list query fails
  await assertSucceeds(aliceDb.collection("playlists").where("ownerId", "==", ALICE_UID).get());
  await assertSucceeds(bobDb.collection("playlists").where("visibility", "==", "public").get());
  await assertFails(bobDb.collection("playlists").get());

  // @DocumentId `id` field rejected
  await assertFails(
    aliceDb.collection("playlists").doc("pl_bad").set({
      id: "pl_bad",
      ownerId: ALICE_UID,
      ownerName: "Alice",
      name: "Bad ID",
      visibility: "private",
      trackCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("CatalogTracks: read-only for signed-in users with published filter; client writes denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("catalog_tracks").doc("cat_1").set({
      trackId: "cat_1",
      title: "Curated Ambient",
      artistName: "Studio Collective",
      streamUrl: "https://api.audius.co/v1/tracks/cat_1/stream",
      durationSeconds: 240,
      downloadable: true,
      published: true,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("catalog_tracks").where("published", "==", true).get());
  await assertFails(aliceDb.collection("catalog_tracks").get());
  await assertFails(
    aliceDb.collection("catalog_tracks").doc("cat_2").set({
      trackId: "cat_2",
      title: "Tamper",
      artistName: "Hacker",
      streamUrl: "https://example.com/a.mp3",
      durationSeconds: 10,
      downloadable: false,
      published: true,
    })
  );
});
