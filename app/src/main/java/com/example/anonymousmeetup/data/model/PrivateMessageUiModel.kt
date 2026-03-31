package com.example.anonymousmeetup.data.model

data class PrivateMessageUiModel(
    val id: String,
    val text: String,
    val timestamp: Long,
    val type: String,
    val isMine: Boolean,
    val location: LocationPayload? = null
)

