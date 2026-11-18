import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from "@firebase/rules-unit-testing";

import {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  Timestamp
} from "firebase/firestore";

const projectId = "demo-neurogram-rules";
const rules = await readFile(new URL("../firestore.rules", import.meta.url), "utf8");

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: { rules }
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    await setDoc(doc(db, "chats", "group"), {
      members: ["alice", "bob", "carol"],
      memberNames: {
        alice: "Alice",
        bob: "Bob",
        carol: "Carol"
      },
      title: "Test group",
      avatarUrl: "",
      isGroup: true,
      createdBy: "alice",
      admins: ["alice"],
      lastMessage: "Группа создана",
      updatedAt: Timestamp.fromMillis(1),
      blockedBy: [],
      mutedFor: [],
      hiddenFor: [],
      typingAt: {}
    });

    await setDoc(doc(db, "chats", "group", "messages", "alice-message"), {
      senderId: "alice",
      senderName: "Alice",
      text: "Original message",
      mediaUrl: "",
      mediaType: "",
      createdAt: Timestamp.fromMillis(1),
      deliveredAt: null,
      readAt: null,
      edited: false,
      reactions: {}
    });
  });
});

after(async () => {
  await testEnv?.cleanup();
});

test("a group member can read the group, but an outsider cannot", async () => {
  const memberDb = testEnv.authenticatedContext("bob").firestore();
  const outsiderDb = testEnv.authenticatedContext("outsider").firestore();

  await assertSucceeds(getDoc(doc(memberDb, "chats", "group")));
  await assertFails(getDoc(doc(outsiderDb, "chats", "group")));
});

test("a regular member cannot replace the administrator list", async () => {
  const db = testEnv.authenticatedContext("bob").firestore();

  await assertFails(updateDoc(doc(db, "chats", "group"), {
    admins: ["bob"]
  }));
});

test("a group member can pin a message using the fields written by the app", async () => {
  const db = testEnv.authenticatedContext("bob").firestore();

  await assertSucceeds(updateDoc(doc(db, "chats", "group"), {
    pinnedMessageId: "alice-message",
    pinnedMessageText: "Original message"
  }));
});

test("a group member cannot rename the group, but its admin can", async () => {
  const memberDb = testEnv.authenticatedContext("bob").firestore();
  const adminDb = testEnv.authenticatedContext("alice").firestore();

  await assertFails(updateDoc(doc(memberDb, "chats", "group"), {
    title: "Changed by member"
  }));

  await assertSucceeds(updateDoc(doc(adminDb, "chats", "group"), {
    title: "Changed by admin"
  }));
});

test("a member can leave a group without removing another member", async () => {
  const db = testEnv.authenticatedContext("bob").firestore();

  await assertSucceeds(updateDoc(doc(db, "chats", "group"), {
    members: ["alice", "carol"],
    hiddenFor: ["bob"]
  }));

  await assertFails(updateDoc(doc(db, "chats", "group"), {
    members: ["alice", "bob"]
  }));
});

test("a member can mute the chat for themselves, not for another user", async () => {
  const db = testEnv.authenticatedContext("bob").firestore();

  await assertSucceeds(updateDoc(doc(db, "chats", "group"), {
    mutedFor: ["bob"]
  }));

  await assertFails(updateDoc(doc(db, "chats", "group"), {
    mutedFor: ["alice"]
  }));
});

test("a member can update only their own typing timestamp", async () => {
  const db = testEnv.authenticatedContext("bob").firestore();

  await assertSucceeds(updateDoc(doc(db, "chats", "group"), {
    "typingAt.bob": Timestamp.now()
  }));

  await assertFails(updateDoc(doc(db, "chats", "group"), {
    "typingAt.alice": Timestamp.now()
  }));
});

test("a member can create a message as themselves", async () => {
  const db = testEnv.authenticatedContext("bob").firestore();

  await assertSucceeds(setDoc(doc(db, "chats", "group", "messages", "bob-message"), {
    senderId: "bob",
    senderName: "Bob",
    text: "Hello",
    mediaUrl: "",
    mediaType: "",
    createdAt: Timestamp.now(),
    deliveredAt: null,
    readAt: null,
    edited: false
  }));
});

test("a non-member cannot create a message", async () => {
  const db = testEnv.authenticatedContext("outsider").firestore();

  await assertFails(setDoc(doc(db, "chats", "group", "messages", "intruder-message"), {
    senderId: "outsider",
    senderName: "Outsider",
    text: "Intrusion",
    mediaUrl: "",
    mediaType: "",
    createdAt: Timestamp.now(),
    deliveredAt: null,
    readAt: null,
    edited: false
  }));
});

test("only the sender can edit a message", async () => {
  const senderDb = testEnv.authenticatedContext("alice").firestore();
  const recipientDb = testEnv.authenticatedContext("bob").firestore();
  const messagePath = ["chats", "group", "messages", "alice-message"];

  await assertFails(updateDoc(doc(recipientDb, ...messagePath), {
    text: "Changed by recipient",
    edited: true,
    editedAt: Timestamp.now()
  }));

  await assertSucceeds(updateDoc(doc(senderDb, ...messagePath), {
    text: "Edited by sender",
    edited: true,
    editedAt: Timestamp.now()
  }));
});

test("only the recipient can update a read receipt", async () => {
  const senderDb = testEnv.authenticatedContext("alice").firestore();
  const recipientDb = testEnv.authenticatedContext("bob").firestore();
  const messagePath = ["chats", "group", "messages", "alice-message"];

  await assertSucceeds(updateDoc(doc(recipientDb, ...messagePath), {
    readAt: Timestamp.now()
  }));

  await assertFails(updateDoc(doc(senderDb, ...messagePath), {
    readAt: Timestamp.now()
  }));
});

test("message text longer than 5000 characters is rejected", async () => {
  const db = testEnv.authenticatedContext("bob").firestore();

  await assertFails(setDoc(doc(db, "chats", "group", "messages", "too-long"), {
    senderId: "bob",
    senderName: "Bob",
    text: "x".repeat(5001),
    mediaUrl: "",
    mediaType: "",
    createdAt: Timestamp.now(),
    deliveredAt: null,
    readAt: null,
    edited: false
  }));
});


test("a user can hide their online and last-seen status only on their own public profile", async () => {
  const aliceDb = testEnv.authenticatedContext("alice").firestore();
  const bobDb = testEnv.authenticatedContext("bob").firestore();
  const aliceProfile = doc(aliceDb, "publicProfiles", "alice");

  await assertSucceeds(setDoc(aliceProfile, {
    uid: "alice",
    online: true,
    showOnlineStatus: true,
    lastSeen: Timestamp.now()
  }));

  await assertSucceeds(updateDoc(aliceProfile, {
    showOnlineStatus: false,
    online: false
  }));

  await assertFails(updateDoc(doc(bobDb, "publicProfiles", "alice"), {
    showOnlineStatus: true,
    online: true
  }));

  const visibleToBob = await assertSucceeds(getDoc(doc(bobDb, "publicProfiles", "alice")));
  assert.equal(visibleToBob.data().showOnlineStatus, false);
  assert.equal(visibleToBob.data().online, false);
});
