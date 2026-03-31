package com.example.anonymousmeetup.data.model

import com.google.firebase.firestore.DocumentId

data class HandshakeEnvelope(
    @DocumentId
    val id: String = "",
    val targetHint: String = "",
    val payload: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

