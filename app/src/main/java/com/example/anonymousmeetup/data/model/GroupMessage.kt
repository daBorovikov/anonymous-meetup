package com.example.anonymousmeetup.data.model

import com.google.firebase.firestore.DocumentId

data class GroupMessage(
    @DocumentId
    val id: String = "",
    val groupHash: String = "",
    val senderAlias: String = "",
    val ciphertext: String = "",
    val nonce: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val ttlSeconds: Long = 604800L,
    val version: Int = 1
)
