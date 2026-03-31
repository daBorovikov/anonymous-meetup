package com.example.anonymousmeetup.data.model

data class HandshakePayload(
    val chatHash: String,
    val initiatorPublicKey: String,
    val initiatorDisplayName: String,
    val createdAt: Long
)

