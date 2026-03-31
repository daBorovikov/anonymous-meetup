package com.example.anonymousmeetup.data.model

data class PrivateTextPayload(
    val type: String = "PRIVATE_TEXT",
    val protocolVersion: Int = 1,
    val conversationId: String,
    val text: String,
    val sentAt: Long,
    val senderAlias: String? = null,
    val sequence: Long? = null
)
