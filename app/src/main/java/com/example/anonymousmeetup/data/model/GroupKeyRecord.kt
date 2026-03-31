package com.example.anonymousmeetup.data.model

data class GroupKeyRecord(
    val groupLocalId: String,
    val localGroupName: String,
    val poolId: String,
    val groupKey: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)
