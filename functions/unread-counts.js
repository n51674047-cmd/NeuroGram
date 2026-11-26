"use strict";

function timestampMillis(value) {
  if (value && typeof value.toMillis === "function") return value.toMillis();
  if (value instanceof Date) {
    const millis = value.getTime();
    return Number.isFinite(millis) ? millis : null;
  }
  if (typeof value === "number" && Number.isFinite(value)) return value;
  return null;
}

const MAX_UNREAD_COUNT = 999999;

function incrementUnreadCounts({
  members = [],
  senderId,
  unreadCounts = {},
  lastReadAt = {},
  messageCreatedAt = null
}) {
  const next = unreadCounts && typeof unreadCounts === "object"
    ? { ...unreadCounts }
    : {};
  const readCursors = lastReadAt && typeof lastReadAt === "object"
    ? lastReadAt
    : {};
  const messageMillis = timestampMillis(messageCreatedAt);
  const visited = new Set();

  for (const memberUid of Array.isArray(members) ? members : []) {
    if (typeof memberUid !== "string" || !memberUid ||
        memberUid === senderId || visited.has(memberUid)) {
      continue;
    }
    visited.add(memberUid);

    const lastReadMillis = timestampMillis(readCursors[memberUid]);
    if (messageMillis !== null && lastReadMillis !== null &&
        messageMillis <= lastReadMillis) {
      continue;
    }

    const current = Number(next[memberUid]);
    const safeCurrent = Number.isFinite(current) && current > 0
      ? Math.min(Math.floor(current), MAX_UNREAD_COUNT)
      : 0;
    next[memberUid] = Math.min(safeCurrent + 1, MAX_UNREAD_COUNT);
  }

  return next;
}

module.exports = { incrementUnreadCounts, MAX_UNREAD_COUNT };
