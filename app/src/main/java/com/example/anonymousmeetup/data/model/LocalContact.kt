package com.example.anonymousmeetup.data.model

data class LocalContact(
    val sessionId: String,
    val alias: String,
    val peerPublicKey: String? = null,
    val currentChatHash: String,
    val addedAt: Long
)

