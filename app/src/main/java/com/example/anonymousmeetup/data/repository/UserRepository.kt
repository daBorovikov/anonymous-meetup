package com.example.anonymousmeetup.data.repository

import com.example.anonymousmeetup.data.model.UserProfile
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.security.AnonymousPools
import com.example.anonymousmeetup.data.security.EncryptionService
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userPreferences: UserPreferences,
    private val encryptionService: EncryptionService
) {
    private val secureRandom = java.security.SecureRandom()

    private fun currentUtcDate(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(System.currentTimeMillis())
    }

    suspend fun register(login: String, password: String) {
        userPreferences.saveNickname(login)
        ensureIdentityKeys()
        ensureDailyKeys()
    }

    suspend fun login(login: String, password: String) {
        userPreferences.saveNickname(login)
        ensureIdentityKeys()
        ensureDailyKeys()
    }

    fun logout() {
    }

    suspend fun ensureIdentityKeys(): IdentityKeyInfo {
        val storedPublic = userPreferences.getIdentityPublicKey()
        val storedPrivate = userPreferences.getIdentityPrivateKey()
        if (!storedPublic.isNullOrBlank() && !storedPrivate.isNullOrBlank()) {
            return IdentityKeyInfo(
                publicKey = storedPublic,
                privateKey = storedPrivate,
                inboundPoolId = AnonymousPools.inboundPrivatePoolFor(storedPublic)
            )
        }

        val keyPair = encryptionService.generateKeyPair()
        val publicKey = encryptionService.exportPublicKey(keyPair.public)
        val privateKey = encryptionService.exportPrivateKey(keyPair.private)
        userPreferences.saveIdentityKeyPair(publicKey, privateKey)
        return IdentityKeyInfo(
            publicKey = publicKey,
            privateKey = privateKey,
            inboundPoolId = AnonymousPools.inboundPrivatePoolFor(publicKey)
        )
    }

    suspend fun ensureDailyKeys(): KeyInfo {
        val currentDate = currentUtcDate()
        val storedDate = userPreferences.getCurrentKeyDate()
        val storedPublicKey = userPreferences.getPublicKey()
        val storedPrivateKey = userPreferences.getPrivateKey()

        return if (storedDate == currentDate && !storedPublicKey.isNullOrBlank() && !storedPrivateKey.isNullOrBlank()) {
            val keyHash = encryptionService.hashPublicKey(storedPublicKey)
            val keyHashes = computeKeyHashes()
            KeyInfo(currentDate, storedPublicKey, storedPrivateKey, keyHash, keyHashes)
        } else {
            val keyPair = encryptionService.generateKeyPair()
            val publicKey = encryptionService.exportPublicKey(keyPair.public)
            val privateKey = encryptionService.exportPrivateKey(keyPair.private)
            val keyHash = encryptionService.hashPublicKey(publicKey)
            userPreferences.saveKeyPairForDate(currentDate, publicKey, privateKey)
            val keyHashes = computeKeyHashes()
            KeyInfo(currentDate, publicKey, privateKey, keyHash, keyHashes)
        }
    }

    private suspend fun computeKeyHashes(): List<String> {
        val dates = userPreferences.getAllKeyDates()
        val hashes = mutableListOf<String>()
        for (date in dates) {
            val pair = userPreferences.getKeyPairForDate(date)
            val publicKey = pair?.first
            if (!publicKey.isNullOrBlank()) {
                hashes.add(encryptionService.hashPublicKey(publicKey))
            }
        }
        return hashes.distinct()
    }

    suspend fun updateLogin(newLogin: String) {
        userPreferences.saveNickname(newLogin)
    }

    suspend fun getOrCreateLocalProfile(): UserProfile {
        val keyInfo = ensureDailyKeys()
        val login = userPreferences.getNickname()?.takeIf { it.isNotBlank() }
            ?: "guest_${UUID.randomUUID().toString().take(8)}"
        if (userPreferences.getNickname().isNullOrBlank()) {
            userPreferences.saveNickname(login)
        }

        return UserProfile(
            localUserId = "local_${login.hashCode()}",
            login = login,
            publicKey = keyInfo.publicKey,
            privateKeyEncrypted = keyInfo.privateKey,
            keyDateUtc = keyInfo.keyDate,
            notificationsEnabled = userPreferences.isNotificationsEnabled(),
            locationEnabled = userPreferences.isLocationTrackingEnabled.first(),
            secureBackupEnabled = userPreferences.isSecureBackupEnabled()
        )
    }

    suspend fun exportBackup(password: String): String {
        val profile = getOrCreateLocalProfile()
        val identity = ensureIdentityKeys()
        val payload = JSONObject()
            .put("login", profile.login)
            .put("publicKey", profile.publicKey)
            .put("privateKey", profile.privateKeyEncrypted)
            .put("keyDateUtc", profile.keyDateUtc ?: "")
            .put("identityPublicKey", identity.publicKey)
            .put("identityPrivateKey", identity.privateKey)
            .put("createdAt", System.currentTimeMillis())
            .toString()
            .toByteArray(Charsets.UTF_8)

        return encryptBackup(payload, password)
    }

    suspend fun importBackup(data: String, password: String) {
        val bytes = decryptBackup(data, password)
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        val login = json.optString("login").ifBlank { null }
        val publicKey = json.optString("publicKey")
        val privateKey = json.optString("privateKey")
        val keyDate = json.optString("keyDateUtc").ifBlank { currentUtcDate() }
        val identityPublicKey = json.optString("identityPublicKey").ifBlank { null }
        val identityPrivateKey = json.optString("identityPrivateKey").ifBlank { null }

        require(publicKey.isNotBlank() && privateKey.isNotBlank()) { "Backup does not contain keys" }

        if (!login.isNullOrBlank()) {
            userPreferences.saveNickname(login)
        }
        userPreferences.saveKeyPairForDate(keyDate, publicKey, privateKey)
        if (!identityPublicKey.isNullOrBlank() && !identityPrivateKey.isNullOrBlank()) {
            userPreferences.saveIdentityKeyPair(identityPublicKey, identityPrivateKey)
        }
    }

    suspend fun getPublicKey(): String = ensureDailyKeys().publicKey

    suspend fun getPrivateKey(): ByteArray = ensureDailyKeys().privateKey.toByteArray(Charsets.UTF_8)

    suspend fun getIdentityPublicKey(): String = ensureIdentityKeys().publicKey

    suspend fun getIdentityPrivateKey(): String = ensureIdentityKeys().privateKey

    private fun encryptBackup(plaintext: ByteArray, password: String): String {
        require(password.isNotBlank()) { "Password is empty" }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val keyBytes = deriveBackupKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext)
        return JSONObject()
            .put("salt", Base64.getEncoder().encodeToString(salt))
            .put("iv", Base64.getEncoder().encodeToString(iv))
            .put("ciphertext", Base64.getEncoder().encodeToString(encrypted))
            .toString()
    }

    private fun decryptBackup(serialized: String, password: String): ByteArray {
        require(password.isNotBlank()) { "Password is empty" }
        val json = JSONObject(serialized)
        val salt = Base64.getDecoder().decode(json.getString("salt"))
        val iv = Base64.getDecoder().decode(json.getString("iv"))
        val ciphertext = Base64.getDecoder().decode(json.getString("ciphertext"))
        val keyBytes = deriveBackupKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveBackupKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 65_536, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    data class KeyInfo(
        val keyDate: String,
        val publicKey: String,
        val privateKey: String,
        val keyHash: String,
        val keyHashes: List<String>
    )

    data class IdentityKeyInfo(
        val publicKey: String,
        val privateKey: String,
        val inboundPoolId: String
    )
}
