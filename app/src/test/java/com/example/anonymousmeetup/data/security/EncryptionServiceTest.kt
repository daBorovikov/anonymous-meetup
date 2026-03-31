package com.example.anonymousmeetup.data.security

import com.example.anonymousmeetup.data.model.AnonymousEnvelope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.util.UUID

class EncryptionServiceTest {

    private lateinit var encryptionService: EncryptionService
    private lateinit var codec: AnonymousMessageCodec

    @Before
    fun setUp() {
        encryptionService = EncryptionService()
        codec = AnonymousMessageCodec(encryptionService)
    }

    @Test
    fun generatesAndImportsKeyPairs() {
        val keyPair = encryptionService.generateKeyPair()
        val publicKey = encryptionService.exportPublicKey(keyPair.public)
        val privateKey = encryptionService.exportPrivateKey(keyPair.private)

        val importedPublic = encryptionService.importPublicKey(publicKey)
        val importedPrivate = encryptionService.importPrivateKey(privateKey)

        assertNotNull(importedPublic)
        assertNotNull(importedPrivate)
        assertEquals(publicKey, encryptionService.exportPublicKey(importedPublic))
        assertEquals(privateKey, encryptionService.exportPrivateKey(importedPrivate))
    }

    @Test
    fun handshakeDerivesSameSharedSecretOnBothSides() {
        val alice = encryptionService.generateKeyPair()
        val bob = encryptionService.generateKeyPair()

        val aliceSecret = encryptionService.deriveSharedSecret(alice.private, bob.public)
        val bobSecret = encryptionService.deriveSharedSecret(bob.private, alice.public)

        assertArrayEquals(aliceSecret, bobSecret)
    }

    @Test
    fun privatePayloadDecryptsOnlyWithCorrectSessionKey() {
        val alice = encryptionService.generateKeyPair()
        val bob = encryptionService.generateKeyPair()
        val eve = encryptionService.generateKeyPair()
        val conversationSeed = codec.generateConversationSeed()

        val aliceShared = encryptionService.deriveSharedSecret(alice.private, bob.public)
        val bobShared = encryptionService.deriveSharedSecret(bob.private, alice.public)
        val eveShared = encryptionService.deriveSharedSecret(eve.private, alice.public)

        val aliceKeyMaterial = codec.deriveConversationKeyMaterial(aliceShared, conversationSeed)
        val bobKeyMaterial = codec.deriveConversationKeyMaterial(bobShared, conversationSeed)
        val eveKeyMaterial = codec.deriveConversationKeyMaterial(eveShared, conversationSeed)

        val envelope = codec.createSymmetricEnvelope(
            poolId = AnonymousPools.conversationPoolFor(conversationSeed),
            keyMaterial = aliceKeyMaterial,
            payload = jsonStringOf(
                "type" to "PRIVATE_TEXT",
                "protocolVersion" to AnonymousMessageCodec.PROTOCOL_VERSION,
                "conversationId" to codec.buildConversationId(conversationSeed),
                "text" to "hello",
                "sentAt" to 1L
            )
        ).withId()

        val decryptedByBob = codec.tryDecryptSymmetricEnvelopeRaw(envelope, bobKeyMaterial)
        val decryptedByEve = codec.tryDecryptSymmetricEnvelopeRaw(envelope, eveKeyMaterial)

        val bobPayload = decryptedByBob?.let(TestJson::parseObject)
        assertEquals("hello", bobPayload?.let { TestJson.string(it, "text") })
        assertNull(decryptedByEve)
        assertNotEquals(
            encryptionService.encodeBase64(aliceKeyMaterial),
            encryptionService.encodeBase64(eveKeyMaterial)
        )
    }

    @Test
    fun tamperedCiphertextAndNonceDoNotDecrypt() {
        val key = encryptionService.randomBytes(32)
        val envelope = codec.createSymmetricEnvelope(
            poolId = AnonymousPools.GROUP_POOLS.first(),
            keyMaterial = key,
            payload = jsonStringOf(
                "type" to "GROUP_TEXT",
                "protocolVersion" to AnonymousMessageCodec.PROTOCOL_VERSION,
                "groupLocalIdOrRoutingHint" to "groupA",
                "text" to "payload"
            )
        ).withId()

        val tamperedCiphertext = envelope.copy(
            ciphertext = envelope.ciphertext.dropLast(2) + "AA"
        )
        val tamperedNonce = envelope.copy(
            nonce = encryptionService.encodeBase64(encryptionService.randomNonce())
        )

        assertNull(codec.tryDecryptSymmetricEnvelopeRaw(tamperedCiphertext, key))
        assertNull(codec.tryDecryptSymmetricEnvelopeRaw(tamperedNonce, key))
    }

    @Test
    fun groupKeyDecryptsOnlyItsOwnMessagesAndNotPrivateMessages() {
        val conversationKey = encryptionService.randomBytes(32)
        val groupKey = encryptionService.randomBytes(32)

        val privateEnvelope = codec.createSymmetricEnvelope(
            poolId = AnonymousPools.PRIVATE_POOLS.first(),
            keyMaterial = conversationKey,
            payload = jsonStringOf(
                "type" to "PRIVATE_TEXT",
                "protocolVersion" to AnonymousMessageCodec.PROTOCOL_VERSION,
                "conversationId" to "conv_1",
                "text" to "private"
            )
        ).withId()

        val groupEnvelope = codec.createSymmetricEnvelope(
            poolId = AnonymousPools.GROUP_POOLS.first(),
            keyMaterial = groupKey,
            payload = jsonStringOf(
                "type" to "GROUP_TEXT",
                "protocolVersion" to AnonymousMessageCodec.PROTOCOL_VERSION,
                "groupLocalIdOrRoutingHint" to "group_1",
                "text" to "group"
            )
        ).withId()

        val privatePayload = codec.tryDecryptSymmetricEnvelopeRaw(privateEnvelope, conversationKey)?.let(TestJson::parseObject)
        val groupPayload = codec.tryDecryptSymmetricEnvelopeRaw(groupEnvelope, groupKey)?.let(TestJson::parseObject)

        assertEquals("private", privatePayload?.let { TestJson.string(it, "text") })
        assertEquals("group", groupPayload?.let { TestJson.string(it, "text") })
        assertNull(codec.tryDecryptSymmetricEnvelopeRaw(privateEnvelope, groupKey))
        assertNull(codec.tryDecryptSymmetricEnvelopeRaw(groupEnvelope, conversationKey))
    }

    @Test
    fun invalidAsymmetricKeyCannotDecryptHandshakePayload() {
        val alice = encryptionService.generateKeyPair()
        val bob = encryptionService.generateKeyPair()
        val eve = encryptionService.generateKeyPair()
        val bobPublicKey = encryptionService.exportPublicKey(bob.public)
        val evePrivateKey = encryptionService.exportPrivateKey(eve.private)

        val envelope = codec.createAsymmetricEnvelope(
            poolId = AnonymousPools.inboundPrivatePoolFor(bobPublicKey),
            recipientPublicKey = bobPublicKey,
            payload = jsonStringOf(
                "type" to "HANDSHAKE_REQUEST",
                "protocolVersion" to AnonymousMessageCodec.PROTOCOL_VERSION,
                "conversationSeed" to codec.generateConversationSeed(),
                "initiatorPublicKey" to encryptionService.exportPublicKey(alice.public)
            )
        ).withId()

        assertThrows(EncryptionException::class.java) {
            encryptionService.decryptMessage(envelope.ciphertext, evePrivateKey)
        }
    }

    private fun AnonymousEnvelope.withId(id: String = UUID.randomUUID().toString()): AnonymousEnvelope {
        return copy(id = id)
    }

    private fun jsonStringOf(vararg entries: Pair<String, Any?>): String {
        return entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${escapeJson(key)}\":${serializeJsonValue(value)}"
        }
    }

    private fun serializeJsonValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Number, is Boolean -> value.toString()
            else -> "\"${escapeJson(value.toString())}\""
        }
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
