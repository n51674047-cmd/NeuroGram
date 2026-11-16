import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from "@firebase/rules-unit-testing";

import { doc, setDoc, updateDoc } from "firebase/firestore";
import { deleteObject, getBytes, ref, uploadBytes } from "firebase/storage";

const projectId = "demo-neurogram-rules";
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


test("a former chat member loses access to media after leaving the chat", async () => {
  const path = "chats/members/media/after-leaving.png";
  const aliceStorage = testEnv.authenticatedContext("alice").storage();
  const bobStorage = testEnv.authenticatedContext("bob").storage();

  await assertSucceeds(uploadBytes(
    ref(aliceStorage, path),
    new Uint8Array([9, 10, 11]),
    { contentType: "image/png" }
  ));
  await assertSucceeds(getBytes(ref(bobStorage, path)));

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "chats", "members"), {
      members: ["alice"]
    });
  });

  await assertFails(getBytes(ref(bobStorage, path)));
  await assertFails(uploadBytes(
    ref(bobStorage, "chats/members/media/after-leaving-2.png"),
    new Uint8Array([12]),
    { contentType: "image/png" }
  ));
});



test("a user can delete their own avatar but cannot delete another user's avatar", async () => {
  const aliceStorage = testEnv.authenticatedContext("alice").storage();
  const bobStorage = testEnv.authenticatedContext("bob").storage();
  const aliceAvatar = ref(aliceStorage, "avatars/alice/old-avatar.png");
  const bobAvatar = ref(bobStorage, "avatars/bob/private-avatar.png");

  await assertSucceeds(uploadBytes(
    aliceAvatar,
    new Uint8Array([1, 2, 3]),
    { contentType: "image/png" }
  ));
  await assertSucceeds(uploadBytes(
    bobAvatar,
    new Uint8Array([4, 5, 6]),
    { contentType: "image/png" }
  ));

  await assertSucceeds(deleteObject(aliceAvatar));
  await assertFails(deleteObject(ref(aliceStorage, "avatars/bob/private-avatar.png")));
});

test("existing avatar and chat media objects cannot be overwritten", async () => {
  const aliceStorage = testEnv.authenticatedContext("alice").storage();
  const avatarRef = ref(aliceStorage, "avatars/alice/avatar-once.png");
  const mediaRef = ref(aliceStorage, "chats/members/media/photo-once.png");

  await assertSucceeds(uploadBytes(
    avatarRef,
    new Uint8Array([1, 2, 3]),
    { contentType: "image/png" }
  ));
  await assertFails(uploadBytes(
    avatarRef,
    new Uint8Array([4, 5, 6]),
    { contentType: "image/png" }
  ));

  await assertSucceeds(uploadBytes(
    mediaRef,
    new Uint8Array([7, 8, 9]),
    { contentType: "image/png" }
  ));
  await assertFails(uploadBytes(
    mediaRef,
    new Uint8Array([10, 11, 12]),
    { contentType: "image/png" }
  ));
});
