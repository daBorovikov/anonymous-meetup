package com.example.anonymousmeetup.data.model

data class HandshakeRequestPayload(
    val type: String = "HANDSHAKE_REQUEST",
    val protocolVersion: Int = 1,
    val conversationSeed: String,
    val initiatorPublicKey: String,
    val initiatorAlias: String? = null,
    val createdAt: Long
)
