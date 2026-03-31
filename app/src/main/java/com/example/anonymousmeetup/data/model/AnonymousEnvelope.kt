package com.example.anonymousmeetup.data.model

import com.google.firebase.firestore.DocumentId

data class AnonymousEnvelope(
    @DocumentId
    val id: String = "",
    val poolId: String = "",
    val ciphertext: String = "",
    val nonce: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val ttlSeconds: Long = 604800L,
    val version: Int = 1
)
