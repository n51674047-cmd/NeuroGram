const test = require("node:test");
const assert = require("node:assert/strict");
const { buildMessagePreview } = require("./message-preview");

test("uses a readable preview for image, video and voice messages", () => {
  assert.equal(buildMessagePreview({ mediaType: "image" }), "📷 Фото");
  assert.equal(buildMessagePreview({ mediaType: "video" }), "▶ Видео");
  assert.equal(buildMessagePreview({ mediaType: "audio" }), "🎤 Голосовое сообщение");
});

test("keeps a caption alongside the media type", () => {
  assert.equal(
    buildMessagePreview({ mediaType: "image", text: "  Смотри сюда  " }),
    "📷 Фото · Смотри сюда"
  );
  assert.equal(
    buildMessagePreview({ mediaType: "video", text: "Новый ролик" }),
    "▶ Видео · Новый ролик"
  );
  assert.equal(
    buildMessagePreview({ mediaType: "audio", text: "Важное сообщение" }),
    "🎤 Голосовое сообщение · Важное сообщение"
  );
});

test("uses a bounded text preview for ordinary messages", () => {
  assert.equal(buildMessagePreview({ text: " Привет " }), "Привет");
  assert.equal(buildMessagePreview({ text: "x".repeat(200) }).length, 140);
});

test("bounds media captions without losing the media label", () => {
  const preview = buildMessagePreview({ mediaType: "image", text: "x".repeat(200) });
  assert.equal(preview.length, 140);
  assert.ok(preview.startsWith("📷 Фото · "));
});

test("handles missing optional message fields", () => {
  assert.equal(buildMessagePreview({}), "");
  assert.equal(buildMessagePreview(null), "");
});
