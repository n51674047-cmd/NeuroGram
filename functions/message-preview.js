function buildMessagePreview(message) {
  const mediaType = typeof message?.mediaType === "string" ? message.mediaType : "";
  const text = typeof message?.text === "string" ? message.text.trim() : "";
  const mediaLabels = {
    image: "📷 Фото",
    video: "▶ Видео",
    audio: "🎤 Голосовое сообщение"
  };
  const mediaLabel = mediaLabels[mediaType];

  if (mediaLabel) {
    const preview = text ? `${mediaLabel} · ${text}` : mediaLabel;
    return preview.slice(0, 140);
  }
  return text.slice(0, 140);
}

module.exports = { buildMessagePreview };
