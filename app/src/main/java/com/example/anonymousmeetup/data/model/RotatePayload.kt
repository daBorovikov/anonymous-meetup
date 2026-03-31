package com.example.anonymousmeetup.data.model

data class RotatePayload(
    val chatHashNew: String,
    val rotateId: String,
    val validFrom: Long,
    val gracePeriodSeconds: Long
)

