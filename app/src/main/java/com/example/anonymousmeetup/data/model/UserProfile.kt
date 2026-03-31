package com.example.anonymousmeetup.data.model

data class UserProfile(
    val localUserId: String,
    val login: String,
    val publicKey: String,
    val privateKeyEncrypted: String,
    val keyDateUtc: String?,
    val notificationsEnabled: Boolean,
    val locationEnabled: Boolean,
    val secureBackupEnabled: Boolean
)

