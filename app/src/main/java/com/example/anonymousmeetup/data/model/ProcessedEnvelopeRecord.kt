package com.example.anonymousmeetup.data.model

data class ProcessedEnvelopeRecord(
    val envelopeId: String,
    val poolId: String,
    val processedAt: Long,
    val outcome: ProcessedEnvelopeOutcome
)
