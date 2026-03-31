package com.example.anonymousmeetup.data.model

import com.google.firebase.firestore.DocumentId

data class HashPoolStat(
    @DocumentId
    val rangeId: String = "",
    val allocatedCount: Long = 0,
    val messagesCount: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

