package com.neurogram.app

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal fun syncNeuroGramFcmToken(uid: String) {
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token -> saveNeuroGramFcmToken(uid, token) }
}

private fun tokenDocumentId(token: String): String = Base64.encodeToString(
    token.toByteArray(Charsets.UTF_8),
    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
)

private fun saveNeuroGramFcmToken(uid: String, token: String) {
    FirebaseFirestore.getInstance()
        .collection("users").document(uid)
        .collection("fcmTokens").document(tokenDocumentId(token))
        .set(mapOf(
            "token" to token,
            "platform" to "android",
            "updatedAt" to FieldValue.serverTimestamp()
        ))
}

internal fun removeNeuroGramFcmToken(uid: String, onComplete: () -> Unit) {
    val messaging = FirebaseMessaging.getInstance()
    messaging.token.addOnCompleteListener { tokenTask ->
        fun revokeLocalToken() {
            messaging.deleteToken().addOnCompleteListener { onComplete() }
        }

        if (!tokenTask.isSuccessful) {
            revokeLocalToken()
        } else {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("fcmTokens").document(tokenDocumentId(tokenTask.result))
                .delete()
                .addOnCompleteListener { revokeLocalToken() }
        }
    }
}

class NovixGramMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            saveNeuroGramFcmToken(uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // While the app is visible, Firestore listeners handle foreground alerts.
        // Suppress FCM here to avoid showing the same message twice.
        if (MainActivity.isAppVisible) return
        val data = message.data
        val chatId = data["chatId"].orEmpty()
        val messageId = data["messageId"].orEmpty()
        if (chatId.isBlank() || messageId.isBlank()) return
        showIncomingMessageNotification(
            applicationContext,
            chatId,
            messageId,
            data["senderName"] ?: "NeuroGram",
            data["text"] ?: ""
        )
    }
}
