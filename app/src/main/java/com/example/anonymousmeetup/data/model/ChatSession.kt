package com.example.anonymousmeetup.data.model

data class ChatSession(
    val sessionId: String,
    val peerDisplayName: String,
    val peerPublicKey: String = "",
    val currentChatHash: String,
    val previousChatHashes: List<String> = emptyList(),
    val sharedSecretRef: String,
    val createdAt: Long,
    val rotateAt: Long? = null,
    val isActive: Boolean = true
)
