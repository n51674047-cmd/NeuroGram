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
    next[memberUid] =
      (Number.isFinite(current) && current > 0 ? Math.floor(current) : 0) + 1;
  }

  return next;
}

module.exports = { incrementUnreadCounts };
