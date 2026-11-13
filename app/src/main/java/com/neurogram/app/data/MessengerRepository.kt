package com.neurogram.app.data

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage

/**
 * Firebase data gateway for the expanded messenger features.
 * Existing chat flows remain untouched while new screens can adopt this API.
 */
class MessengerRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private fun requireUid(): String =
        auth.currentUser?.uid ?: error("Для этого действия необходимо войти в NeuroGram")

    fun observePublicChannels(
        onResult: (List<NgChannel>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = db.collection("channels")
        .whereEqualTo("isPublic", true)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) { onError(error); return@addSnapshotListener }
            onResult(snapshot?.documents.orEmpty().mapNotNull { doc ->
                doc.toObject(NgChannel::class.java)?.copy(id = doc.id)
            })
        }

    fun createChannel(
        title: String,
        username: String,
        description: String,
        isPublic: Boolean,
        onComplete: (String?, Exception?) -> Unit
    ) {
        val uid = requireUid()
        val cleanTitle = title.trim()
        val cleanUsername = username.trim().removePrefix("@").lowercase()
        require(cleanTitle.isNotEmpty()) { "Укажите название канала" }
        require(cleanUsername.matches(Regex("[a-z0-9_]{5,32}"))) {
            "Username канала: 5–32 символа, латинские буквы, цифры и _"
        }
        val ref = db.collection("channels").document()
        val data = mapOf(
            "title" to cleanTitle,
            "username" to cleanUsername,
            "description" to description.trim().take(255),
            "ownerId" to uid,
            "admins" to listOf(uid),
            "members" to listOf(uid),
            "memberCount" to 1L,
            "isPublic" to isPublic,
            "createdAt" to FieldValue.serverTimestamp()
        )
        ref.set(data).addOnSuccessListener { onComplete(ref.id, null) }
            .addOnFailureListener { onComplete(null, it) }
    }

    fun joinChannel(channelId: String, onComplete: (Exception?) -> Unit) {
        val uid = requireUid()
        val ref = db.collection("channels").document(channelId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val members = (snap.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
            if (uid !in members) {
                tx.update(ref, "members", FieldValue.arrayUnion(uid))
                tx.update(ref, "memberCount", FieldValue.increment(1))
            }
        }.addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun leaveChannel(channelId: String, onComplete: (Exception?) -> Unit) {
        val uid = requireUid()
        val ref = db.collection("channels").document(channelId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            require(snap.getString("ownerId") != uid) { "Владелец должен передать права перед выходом" }
            val members = (snap.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
            if (uid in members) {
                tx.update(ref, "members", FieldValue.arrayRemove(uid))
                tx.update(ref, "memberCount", FieldValue.increment(-1))
            }
        }.addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun observeChannelPosts(
        channelId: String,
        onResult: (List<NgChannelPost>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = db.collection("channels").document(channelId)
        .collection("posts").orderBy("createdAt", Query.Direction.ASCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) { onError(error); return@addSnapshotListener }
            onResult(snapshot?.documents.orEmpty().mapNotNull { doc ->
                doc.toObject(NgChannelPost::class.java)?.copy(id = doc.id, channelId = channelId)
            })
        }

    fun publishChannelPost(
        channelId: String,
        text: String,
        mediaUrl: String = "",
        mediaType: String = "",
        onComplete: (String?, Exception?) -> Unit
    ) {
        val uid = requireUid()
        val body = text.trim()
        require(body.isNotEmpty() || mediaUrl.isNotBlank()) { "Публикация не может быть пустой" }
        require(mediaType in listOf("", "image", "video", "file")) { "Неподдерживаемый тип вложения" }
        val ref = db.collection("channels").document(channelId).collection("posts").document()
        ref.set(mapOf(
            "authorId" to uid,
            "text" to body.take(5000),
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType,
            "createdAt" to FieldValue.serverTimestamp(),
            "views" to 0L,
            "reactions" to emptyMap<String, List<String>>()
        )).addOnSuccessListener { onComplete(ref.id, null) }
            .addOnFailureListener { onComplete(null, it) }
    }

    fun setTyping(chatId: String, typing: Boolean, onComplete: (Exception?) -> Unit = {}) {
        val uid = requireUid()
        db.collection("chats").document(chatId).collection("typing").document(uid)
            .set(mapOf(
                "uid" to uid,
                "typing" to typing,
                "updatedAt" to FieldValue.serverTimestamp()
            )).addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun observeTyping(
        chatId: String,
        onResult: (List<NgTypingState>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = db.collection("chats").document(chatId)
        .collection("typing").addSnapshotListener { snapshot, error ->
            if (error != null) { onError(error); return@addSnapshotListener }
            val now = System.currentTimeMillis()
            onResult(snapshot?.documents.orEmpty().mapNotNull { doc ->
                doc.toObject(NgTypingState::class.java)
            }.filter { state ->
                state.typing && state.updatedAt?.toDate()?.time?.let { now - it < 15_000L } == true
            })
        }

    fun uploadChatAttachment(
        chatId: String,
        uri: Uri,
        mimeType: String,
        onComplete: (String?, Exception?) -> Unit
    ) {
        require(mimeType.startsWith("image/") || mimeType.startsWith("video/") ||
            mimeType == "application/pdf" || mimeType.startsWith("text/")) {
            "Этот тип файла пока не поддерживается"
        }
        val uid = requireUid()
        val extension = mimeType.substringAfter('/', "bin").replace(Regex("[^a-zA-Z0-9]"), "").take(8)
        val ref = storage.reference.child("chats/$chatId/media/$uid-${System.currentTimeMillis()}.$extension")
        ref.putFile(uri).continueWithTask { task ->
            if (!task.isSuccessful) throw (task.exception ?: IllegalStateException("Не удалось загрузить файл"))
            ref.downloadUrl
        }.addOnSuccessListener { onComplete(it.toString(), null) }
            .addOnFailureListener { onComplete(null, it) }
    }

    fun createCallInvite(
        chatId: String,
        calleeId: String,
        kind: String,
        onComplete: (String?, Exception?) -> Unit
    ) {
        require(kind == "audio" || kind == "video") { "Тип звонка должен быть audio или video" }
        val callerId = requireUid()
        val ref = db.collection("calls").document()
        ref.set(mapOf(
            "chatId" to chatId,
            "callerId" to callerId,
            "calleeId" to calleeId,
            "kind" to kind,
            "state" to "ringing",
            "createdAt" to Timestamp.now()
        )).addOnSuccessListener { onComplete(ref.id, null) }
            .addOnFailureListener { onComplete(null, it) }
    }
}
