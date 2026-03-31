package com.example.anonymousmeetup.data.local

import com.example.anonymousmeetup.data.model.UserProfile
import com.example.anonymousmeetup.data.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalProfileStore @Inject constructor(
    private val userPreferences: UserPreferences
) {
    fun observeProfile(): Flow<UserProfile> {
        return combine(
            userPreferences.nickname,
            userPreferences.publicKey,
            userPreferences.privateKey,
            userPreferences.keyDate
        ) { nickname, publicKey, privateKey, keyDate ->
            Quad(
                nickname = nickname ?: "guest_${UUID.randomUUID().toString().take(6)}",
                publicKey = publicKey ?: "",
                privateKey = privateKey ?: "",
                keyDate = keyDate
            )
        }.combine(userPreferences.notificationsEnabled) { quad, notifications ->
            Pair(quad, notifications)
        }.combine(userPreferences.isLocationTrackingEnabled) { state, location ->
            Triple(state.first, state.second, location)
        }.combine(userPreferences.secureBackupEnabled) { state, backup ->
            val quad = state.first
            val notifications = state.second
            val location = state.third
            UserProfile(
                localUserId = "local_${quad.nickname.hashCode()}",
                login = quad.nickname,
                publicKey = quad.publicKey,
                privateKeyEncrypted = quad.privateKey,
                keyDateUtc = quad.keyDate,
                notificationsEnabled = notifications,
                locationEnabled = location,
                secureBackupEnabled = backup
            )
        }
    }

    suspend fun getProfile(): UserProfile = observeProfile().first()

    private data class Quad(
        val nickname: String,
        val publicKey: String,
        val privateKey: String,
        val keyDate: String?
    )
}
