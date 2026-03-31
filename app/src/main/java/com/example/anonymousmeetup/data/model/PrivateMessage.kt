package com.example.anonymousmeetup.data.model

import com.google.firebase.firestore.DocumentId

data class PrivateMessage(
    @DocumentId
    val id: String = "",
    val chatHash: String = "",
    val ciphertext: String = "",
    val nonce: String = "",
    val version: Int = 1,
    val ttlSeconds: Long = 2_592_000L,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "TEXT"
)

