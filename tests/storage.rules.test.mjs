import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from "@firebase/rules-unit-testing";

import { doc, setDoc } from "firebase/firestore";
import { getBytes, ref, uploadBytes } from "firebase/storage";

const projectId = "demo-neurogram-storage-rules";
const firestoreRules = await readFile(new URL("../firestore.rules", import.meta.url), "utf8");
const storageRules = await readFile(new URL("../storage.rules", import.meta.url), "utf8");

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: { rules: firestoreRules },
    storage: { rules: storageRules }
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.clearStorage();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "chats", "members"), {
      members: ["alice", "bob"],
      isGroup: false
    });
  });
});

after(async () => {
  await testEnv?.cleanup();
});

test("an owner can upload an avatar and another signed-in user can read it", async () => {
  const path = "avatars/alice/avatar.png";
  const aliceStorage = testEnv.authenticatedContext("alice").storage();
  const bobStorage = testEnv.authenticatedContext("bob").storage();
  const anonymousStorage = testEnv.unauthenticatedContext().storage();

  await assertSucceeds(uploadBytes(
    ref(aliceStorage, path),
    new Uint8Array([1, 2, 3]),
    { contentType: "image/png" }
  ));

  const downloaded = await assertSucceeds(getBytes(ref(bobStorage, path)));
  assert.equal(downloaded.byteLength, 3);
  await assertFails(getBytes(ref(anonymousStorage, path)));
});

test("a user cannot upload an avatar into another user's folder", async () => {
  const aliceStorage = testEnv.authenticatedContext("alice").storage();

  await assertFails(uploadBytes(
    ref(aliceStorage, "avatars/bob/avatar.png"),
    new Uint8Array([1]),
    { contentType: "image/png" }
  ));
});

test("avatar uploads reject non-images and files at the 5 MiB limit", async () => {
  const aliceStorage = testEnv.authenticatedContext("alice").storage();

  await assertFails(uploadBytes(
    ref(aliceStorage, "avatars/alice/not-an-image.txt"),
    new Uint8Array([1]),
    { contentType: "text/plain" }
  ));

  await assertFails(uploadBytes(
    ref(aliceStorage, "avatars/alice/too-large.png"),
    new Uint8Array(5 * 1024 * 1024),
    { contentType: "image/png" }
  ));
});

test("only chat members can upload and read chat media", async () => {
  const path = "chats/members/media/photo.png";
  const aliceStorage = testEnv.authenticatedContext("alice").storage();
  const bobStorage = testEnv.authenticatedContext("bob").storage();
  const outsiderStorage = testEnv.authenticatedContext("outsider").storage();

  await assertSucceeds(uploadBytes(
    ref(aliceStorage, path),
    new Uint8Array([4, 5, 6]),
    { contentType: "image/png" }
  ));

  const downloaded = await assertSucceeds(getBytes(ref(bobStorage, path)));
  assert.equal(downloaded.byteLength, 3);
  await assertFails(getBytes(ref(outsiderStorage, path)));

  await assertFails(uploadBytes(
    ref(outsiderStorage, "chats/members/media/intruder.png"),
    new Uint8Array([7]),
    { contentType: "image/png" }
  ));
});

test("chat members cannot upload unsupported media types", async () => {
  const aliceStorage = testEnv.authenticatedContext("alice").storage();

  await assertFails(uploadBytes(
    ref(aliceStorage, "chats/members/media/document.pdf"),
    new Uint8Array([8]),
    { contentType: "application/pdf" }
  ));
});
