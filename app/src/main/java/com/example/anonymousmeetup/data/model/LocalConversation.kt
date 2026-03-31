package com.example.anonymousmeetup.data.model

data class LocalConversation(
    val conversationId: String,
    val peerPublicKey: String,
    val localAlias: String? = null,
    val sessionStatus: SessionStatus = SessionStatus.PENDING,
    val sharedSecretRef: String,
    val poolId: String,
    val conversationSeed: String,
    val createdAt: Long,
    val updatedAt: Long,
    val acceptedAt: Long? = null,
    val lastMessageAt: Long? = null,
    val isInitiator: Boolean = false
)
