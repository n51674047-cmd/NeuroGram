const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

exports.sendNewMessagePush = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message || typeof message.senderId !== "string") return;
    const text = typeof message.text === "string" ? message.text : "";
    const mediaType = typeof message.mediaType === "string" ? message.mediaType : "";
    if (!text && !mediaType) return;

    const db = getFirestore();
    const chatRef = db.collection("chats").doc(event.params.chatId);
    const chatSnap = await chatRef.get();
    if (!chatSnap.exists) return;
    const chat = chatSnap.data() || {};
    const members = Array.isArray(chat.members) ? chat.members : [];
    const recipients = members.filter((uid) => uid && uid !== message.senderId);
    const mutedFor = Array.isArray(chat.mutedFor) ? chat.mutedFor : [];
    const senderName = typeof message.senderName === "string" && message.senderName
      ? message.senderName : "Новое сообщение";
    const preview = mediaType === "image" ? "📷 Фото"
      : mediaType === "video" ? "▶ Видео" : text.slice(0, 140);

    for (const recipientUid of recipients) {
      if (mutedFor.includes(recipientUid)) continue;
      const tokenSnap = await db.collection("users").doc(recipientUid)
        .collection("fcmTokens").get();
      const tokenDocs = tokenSnap.docs.filter((doc) => typeof doc.data().token === "string");
      if (tokenDocs.length === 0) continue;

      const title = chat.isGroup === true
        ? (typeof chat.title === "string" ? chat.title : "Групповой чат") + " · " + senderName
        : senderName;
      const payload = {
        data: {
          chatId: String(event.params.chatId),
          messageId: String(event.params.messageId),
          senderName: String(title).slice(0, 80),
          text: preview
        },
        android: { priority: "high" }
      };

      for (let i = 0; i < tokenDocs.length; i += 500) {
        const chunk = tokenDocs.slice(i, i + 500);
        const tokens = chunk.map((doc) => doc.data().token);
        const response = await getMessaging().sendEachForMulticast({ ...payload, tokens });
        const removals = [];
        response.responses.forEach((result, index) => {
          if (!result.success) {
            const code = result.error?.code || "";
            if (code.includes("registration-token-not-registered") ||
                code.includes("invalid-registration-token")) {
              removals.push(chunk[index].ref.delete());
            }
          }
        });
        if (removals.length) await Promise.all(removals);
      }
    }

    await chatRef.set({ lastPushAt: FieldValue.serverTimestamp() }, { merge: true });
  }
);
