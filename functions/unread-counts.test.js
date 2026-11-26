"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const { incrementUnreadCounts, MAX_UNREAD_COUNT } = require("./unread-counts");

const timestamp = (millis) => ({ toMillis: () => millis });

test("increments every recipient but never the sender", () => {
  const result = incrementUnreadCounts({
    members: ["alice", "bob", "carol"],
    senderId: "alice",
    unreadCounts: { alice: 4, bob: 2, carol: 0 },
    messageCreatedAt: timestamp(500)
  });
  assert.deepEqual(result, { alice: 4, bob: 3, carol: 1 });
});

test("does not count a message at or before the recipient read cursor", () => {
  const result = incrementUnreadCounts({
    members: ["alice", "bob", "carol"],
    senderId: "alice",
    unreadCounts: { bob: 3, carol: 1 },
    lastReadAt: { bob: timestamp(500), carol: timestamp(499) },
    messageCreatedAt: timestamp(500)
  });
  assert.deepEqual(result, { bob: 3, carol: 2 });
});

test("handles duplicate members and malformed counters safely", () => {
  const result = incrementUnreadCounts({
    members: ["alice", "bob", "bob", "", null],
    senderId: "alice",
    unreadCounts: { bob: "not-a-number" },
    messageCreatedAt: new Date(1000)
  });
  assert.deepEqual(result, { bob: 1 });
});

test("leaves counts unchanged when all recipients have already read the message", () => {
  const result = incrementUnreadCounts({
    members: ["alice", "bob"],
    senderId: "alice",
    unreadCounts: { bob: 7 },
    lastReadAt: { bob: timestamp(2000) },
    messageCreatedAt: timestamp(1000)
  });
  assert.deepEqual(result, { bob: 7 });
});

test("never exceeds the Firestore rule maximum", () => {
  const result = incrementUnreadCounts({
    members: ["alice", "bob"],
    senderId: "alice",
    unreadCounts: { bob: MAX_UNREAD_COUNT - 1 },
    messageCreatedAt: timestamp(1000)
  });
  assert.equal(result.bob, MAX_UNREAD_COUNT);
  assert.equal(
    incrementUnreadCounts({
      members: ["alice", "bob"],
      senderId: "alice",
      unreadCounts: { bob: MAX_UNREAD_COUNT },
      messageCreatedAt: timestamp(2000)
    }).bob,
    MAX_UNREAD_COUNT
  );
});

test("normalizes fractional and negative counters before incrementing", () => {
  const result = incrementUnreadCounts({
    members: ["alice", "bob", "carol"],
    senderId: "alice",
    unreadCounts: { bob: 2.9, carol: -5 },
    messageCreatedAt: timestamp(1000)
  });
  assert.deepEqual(result, { bob: 3, carol: 1 });
});
