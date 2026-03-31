package com.example.anonymousmeetup.data.model

data class DebugTraceEntry(
    val id: String,
    val tag: String,
    val message: String,
    val level: String,
    val timestamp: Long
)
