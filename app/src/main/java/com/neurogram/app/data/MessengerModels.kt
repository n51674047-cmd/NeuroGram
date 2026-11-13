package com.neurogram.app.data

import com.google.firebase.Timestamp

/**
 * Shared domain models for the expanded NeuroGram messenger.
 * Firestore documents are mapped explicitly so UI code does not depend on raw maps.
 */
data class NgProfile(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val online: Boolean = false,
    val lastSeen: Timestamp? = null
)

data class NgChannel(
    val id: String = "",
    val title: String = "",
    val username: String = "",
    val description: String = "",
    val avatarUrl: String = "",
    val ownerId: String = "",
    val memberCount: Long = 0,
    val isPublic: Boolean = true,
    val createdAt: Timestamp? = null
)

data class NgChannelPost(
    val id: String = "",
    val channelId: String = "",
    val authorId: String = "",
    val text: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "",
    val createdAt: Timestamp? = null,
    val views: Long = 0,
    val reactions: Map<String, List<String>> = emptyMap()
)

data class NgTypingState(
    val uid: String = "",
    val typing: Boolean = false,
    val updatedAt: Timestamp? = null
)

data class NgCallSession(
    val id: String = "",
    val chatId: String = "",
    val callerId: String = "",
    val calleeId: String = "",
    val kind: String = "audio",
    val state: String = "ringing",
    val createdAt: Timestamp? = null
)

data class NgStickerPack(
    val id: String = "",
    val title: String = "",
    val creatorId: String = "",
    val coverUrl: String = "",
    val stickerUrls: List<String> = emptyList()
)
