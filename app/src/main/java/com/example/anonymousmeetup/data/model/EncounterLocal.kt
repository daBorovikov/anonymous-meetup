package com.example.anonymousmeetup.data.model

data class EncounterLocal(
    val encounterId: String,
    val alias: String? = null,
    val publicKeyFingerprint: String? = null,
    val groupHash: String? = null,
    val groupName: String? = null,
    val distanceMeters: Double = 0.0,
    val happenedAt: Long = System.currentTimeMillis()
)

