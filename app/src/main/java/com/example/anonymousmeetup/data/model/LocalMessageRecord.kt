package com.example.anonymousmeetup.data.model

data class LocalMessageRecord(
    val localMessageId: String,
    val conversationIdOrGroupLocalId: String,
    val direction: MessageDirection,
    val type: LocalMessageType,
    val text: String? = null,
    val rawPayloadJson: String? = null,
    val timestamp: Long,
    val sourceEnvelopeId: String? = null,
    val isRead: Boolean = false
)
