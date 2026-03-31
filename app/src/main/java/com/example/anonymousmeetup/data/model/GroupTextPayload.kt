package com.example.anonymousmeetup.data.model

data class GroupTextPayload(
    val type: String = "GROUP_TEXT",
    val protocolVersion: Int = 1,
    val groupLocalIdOrRoutingHint: String,
    val text: String,
    val sentAt: Long,
    val senderAlias: String? = null
)
