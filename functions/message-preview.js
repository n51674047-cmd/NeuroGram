function buildMessagePreview(message) {
  const mediaType = typeof message?.mediaType === "string" ? message.mediaType : "";
  const text = typeof message?.text === "string" ? message.text : "";

  if (mediaType === "image") return "📷 Фото";
  if (mediaType === "video") return "▶ Видео";
  if (mediaType === "audio") return "🎤 Голосовое сообщение";
  return text.slice(0, 140);
}

module.exports = { buildMessagePreview };
