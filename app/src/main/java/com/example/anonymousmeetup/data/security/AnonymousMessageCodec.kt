package com.example.anonymousmeetup.data.security

import com.example.anonymousmeetup.data.model.AnonymousEnvelope
import com.example.anonymousmeetup.data.model.GroupKeyRecord
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnonymousMessageCodec @Inject constructor(
    private val encryptionService: EncryptionService
) {
    fun generateConversationSeed(): String = encryptionService.encodeBase64(encryptionService.randomBytes(32))

    fun generateGroupKey(): String = encryptionService.encodeBase64(encryptionService.randomBytes(32))

    fun buildConversationId(conversationSeed: String): String = "conv_${shortHash(conversationSeed)}"

    fun buildGroupJoinToken(record: GroupKeyRecord): String {
        return listOf(
            GROUP_TOKEN_VERSION,
            encodeTokenPart(record.groupLocalId),
            encodeTokenPart(record.localGroupName),
            encodeTokenPart(record.poolId),
            encodeTokenPart(record.groupKey),
            record.createdAt.toString()
        ).joinToString(separator = ".")
    }

    fun parseGroupJoinToken(token: String): GroupKeyRecord? {
        return parseStructuredGroupJoinToken(token) ?: parseLegacyJsonGroupJoinToken(token)
    }

    fun createAsymmetricEnvelope(
        poolId: String,
        recipientPublicKey: String,
        payloadJson: JSONObject,
        timestamp: Long = System.currentTimeMillis(),
        ttlSeconds: Long = DEFAULT_HANDSHAKE_TTL_SECONDS
    ): AnonymousEnvelope = createAsymmetricEnvelope(
        poolId = poolId,
        recipientPublicKey = recipientPublicKey,
        payload = payloadJson.toString(),
        timestamp = timestamp,
        ttlSeconds = ttlSeconds
    )

    fun createAsymmetricEnvelope(
        poolId: String,
        recipientPublicKey: String,
        payload: String,
        timestamp: Long = System.currentTimeMillis(),
        ttlSeconds: Long = DEFAULT_HANDSHAKE_TTL_SECONDS
    ): AnonymousEnvelope {
        val ciphertext = encryptionService.encryptMessage(payload, recipientPublicKey)
        val outerNonce = encryptionService.encodeBase64(encryptionService.randomBytes(12))
        return AnonymousEnvelope(
            id = "",
            poolId = poolId,
            ciphertext = ciphertext,
            nonce = outerNonce,
            timestamp = timestamp,
            ttlSeconds = ttlSeconds,
            version = PROTOCOL_VERSION
        )
    }

    fun tryDecryptAsymmetricEnvelope(envelope: AnonymousEnvelope, privateKey: String): JSONObject? {
        return runCatching {
            val payload = tryDecryptAsymmetricEnvelopeRaw(envelope, privateKey) ?: return null
            JSONObject(payload)
        }.getOrNull()
    }

    fun tryDecryptAsymmetricEnvelopeRaw(envelope: AnonymousEnvelope, privateKey: String): String? {
        return runCatching {
            encryptionService.decryptMessage(envelope.ciphertext, privateKey)
        }.getOrNull()
    }

    fun deriveConversationKeyMaterial(sharedSecret: ByteArray, conversationSeed: String): ByteArray {
        return encryptionService.hmacSha256(sharedSecret, conversationSeed.toByteArray(Charsets.UTF_8)).copyOf(32)
    }

    fun createSymmetricEnvelope(
        poolId: String,
        keyMaterial: ByteArray,
        payloadJson: JSONObject,
        timestamp: Long = System.currentTimeMillis(),
        ttlSeconds: Long = DEFAULT_MESSAGE_TTL_SECONDS
    ): AnonymousEnvelope = createSymmetricEnvelope(
        poolId = poolId,
        keyMaterial = keyMaterial,
        payload = payloadJson.toString(),
        timestamp = timestamp,
        ttlSeconds = ttlSeconds
    )

    fun createSymmetricEnvelope(
        poolId: String,
        keyMaterial: ByteArray,
        payload: String,
        timestamp: Long = System.currentTimeMillis(),
        ttlSeconds: Long = DEFAULT_MESSAGE_TTL_SECONDS
    ): AnonymousEnvelope {
        val nonce = encryptionService.randomNonce()
        val messageKey = encryptionService.deriveMessageKey(keyMaterial, nonce)
        val encrypted = encryptionService.encryptAead(messageKey, payload.toByteArray(Charsets.UTF_8), nonce)
        return AnonymousEnvelope(
            id = "",
            poolId = poolId,
            ciphertext = encrypted.ciphertext,
            nonce = encrypted.nonce,
            timestamp = timestamp,
            ttlSeconds = ttlSeconds,
            version = PROTOCOL_VERSION
        )
    }

    fun tryDecryptSymmetricEnvelope(envelope: AnonymousEnvelope, keyMaterial: ByteArray): JSONObject? {
        return runCatching {
            val decrypted = tryDecryptSymmetricEnvelopeRaw(envelope, keyMaterial) ?: return null
            JSONObject(decrypted)
        }.getOrNull()
    }

    fun tryDecryptSymmetricEnvelopeRaw(envelope: AnonymousEnvelope, keyMaterial: ByteArray): String? {
        return runCatching {
            val nonce = encryptionService.decodeBase64(envelope.nonce)
            val messageKey = encryptionService.deriveMessageKey(keyMaterial, nonce)
            val decrypted = encryptionService.decryptAead(
                key = messageKey,
                payload = EncryptedPayload(
                    ciphertext = envelope.ciphertext,
                    nonce = envelope.nonce
                )
            )
            String(decrypted, Charsets.UTF_8)
        }.getOrNull()
    }

    fun newLocalMessageId(): String = UUID.randomUUID().toString()

    fun shortHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }.take(24)
    }

    private fun parseStructuredGroupJoinToken(token: String): GroupKeyRecord? {
        val parts = token.split('.')
        if (parts.size != 6 || parts.firstOrNull() != GROUP_TOKEN_VERSION) return null
        return runCatching {
            GroupKeyRecord(
                groupLocalId = decodeTokenPart(parts[1]),
                localGroupName = decodeTokenPart(parts[2]).ifBlank { "Ãðóïïà" },
                poolId = decodeTokenPart(parts[3]),
                groupKey = decodeTokenPart(parts[4]),
                createdAt = parts[5].toLong(),
                updatedAt = parts[5].toLong()
            )
        }.getOrNull()
    }

    private fun parseLegacyJsonGroupJoinToken(token: String): GroupKeyRecord? {
        return runCatching {
            val decoded = Base64.getUrlDecoder().decode(token)
            val json = JSONObject(String(decoded, Charsets.UTF_8))
            GroupKeyRecord(
                groupLocalId = json.getString("groupLocalId"),
                localGroupName = json.optString("localGroupName").ifBlank { "Ãðóïïà" },
                poolId = json.getString("poolId"),
                groupKey = json.getString("groupKey"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }.getOrNull()
    }

    private fun encodeTokenPart(value: String): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    }

    private fun decodeTokenPart(value: String): String {
        return String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }

    companion object {
        const val PROTOCOL_VERSION = 1
        const val DEFAULT_MESSAGE_TTL_SECONDS = 2_592_000L
        const val DEFAULT_HANDSHAKE_TTL_SECONDS = 86_400L
        private const val GROUP_TOKEN_VERSION = "v1"
    }
}
