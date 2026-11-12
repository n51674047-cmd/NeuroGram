package com.neurogram.app

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal fun syncNovixGramFcmToken(uid: String) {
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token -> saveNovixGramFcmToken(uid, token) }
}

private fun saveNovixGramFcmToken(uid: String, token: String) {
    val tokenId = Base64.encodeToString(
        token.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
    FirebaseFirestore.getInstance()
        .collection("users").document(uid)
        .collection("fcmTokens").document(tokenId)
        .set(mapOf(
            "token" to token,
            "platform" to "android",
            "updatedAt" to FieldValue.serverTimestamp()
        ))
}

class NovixGramMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            saveNovixGramFcmToken(uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val chatId = data["chatId"].orEmpty()
        val messageId = data["messageId"].orEmpty()
        if (chatId.isBlank() || messageId.isBlank()) return
        showIncomingMessageNotification(
            applicationContext,
            chatId,
            messageId,
            data["senderName"] ?: "NovixGram",
            data["text"] ?: ""
        )
    }
}
