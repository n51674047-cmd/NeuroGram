const test = require("node:test");
const assert = require("node:assert/strict");
const { buildMessagePreview } = require("./message-preview");

test("uses a readable preview for image, video and voice messages", () => {
  assert.equal(buildMessagePreview({ mediaType: "image" }), "📷 Фото");
  assert.equal(buildMessagePreview({ mediaType: "video" }), "▶ Видео");
  assert.equal(buildMessagePreview({ mediaType: "audio" }), "🎤 Голосовое сообщение");
});

test("uses a bounded text preview for ordinary messages", () => {
  assert.equal(buildMessagePreview({ text: "Привет" }), "Привет");
  assert.equal(buildMessagePreview({ text: "x".repeat(200) }).length, 140);
});

test("handles missing optional message fields", () => {
  assert.equal(buildMessagePreview({}), "");
  assert.equal(buildMessagePreview(null), "");
});
