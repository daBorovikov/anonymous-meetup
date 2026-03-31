package com.example.anonymousmeetup.data.model

data class HandshakeAcceptPayload(
    val type: String = "HANDSHAKE_ACCEPT",
    val protocolVersion: Int = 1,
    val conversationSeed: String,
    val responderPublicKey: String,
    val acceptedAt: Long
)
