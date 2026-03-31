package com.example.anonymousmeetup.data.model

data class LocationMessagePayload(
    val type: String = "LOCATION",
    val protocolVersion: Int = 1,
    val conversationId: String,
    val latitude: Double,
    val longitude: Double,
    val sentAt: Long,
    val senderAlias: String? = null
)
