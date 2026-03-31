package com.example.anonymousmeetup.data.security

import com.example.anonymousmeetup.data.model.AnonymousEnvelope
import com.example.anonymousmeetup.data.model.GroupKeyRecord
import com.example.anonymousmeetup.data.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.system.measureTimeMillis

class AnonymousTransportFlowTest {

    private lateinit var encryptionService: EncryptionService
    private lateinit var codec: AnonymousMessageCodec

    @Before
    fun setUp() {
        encryptionService = EncryptionService()
        codec = AnonymousMessageCodec(encryptionService)
    }

    @Test
    fun handshakeLifecycleRoutesOnlyToIntendedParticipants() {
        val alice = SimulatedAnonymousClient("Alice", encryptionService, codec)
        val bob = SimulatedAnonymousClient("Bob", encryptionService, codec)
        val eve = SimulatedAnonymousClient("Eve", encryptionService, codec)

        val (conversationId, request) = alice.startHandshake(bob.publicKey, "Alice local")
        val requestPayload = codec.tryDecryptAsymmetricEnvelopeRaw(request, bob.privateKey)?.let(TestJson::parseObject)

        assertEquals("HANDSHAKE_REQUEST", requestPayload?.let { TestJson.string(it, "type") })
        assertEquals(AnonymousMessageCodec.PROTOCOL_VERSION, requestPayload?.let { TestJson.int(it, "protocolVersion") })
        assertEquals(alice.publicKey, requestPayload?.let { TestJson.string(it, "initiatorPublicKey") })
        assertNull(codec.tryDecryptAsymmetricEnvelopeRaw(request, eve.privateKey))

        val responseFromBob = bob.receive(request)
        eve.receive(request)

        assertEquals(SessionStatus.ACCEPTED, bob.sessionStatus(conversationId))
        assertFalse(eve.hasConversation(conversationId))
        assertEquals(1, responseFromBob.size)

        alice.receive(responseFromBob.single())

        assertEquals(SessionStatus.ACCEPTED, alice.sessionStatus(conversationId))
        assertNotNull(alice.conversation(conversationId))
        assertNotNull(bob.conversation(conversationId))

        val privateEnvelope = alice.sendPrivateText(conversationId, "hello bob")
        bob.receive(privateEnvelope)
        eve.receive(privateEnvelope)

        assertEquals(SessionStatus.ACTIVE, alice.sessionStatus(conversationId))
        assertEquals(SessionStatus.ACTIVE, bob.sessionStatus(conversationId))
        assertEquals(listOf("hello bob"), bob.privateTexts(conversationId))
        assertTrue(eve.privateTexts(conversationId).isEmpty())
    }

    @Test
    fun repeatedHandshakeDoesNotCreateDuplicateConversation() {
        val alice = SimulatedAnonymousClient("Alice", encryptionService, codec)
        val bob = SimulatedAnonymousClient("Bob", encryptionService, codec)
        val sharedSeed = codec.generateConversationSeed()

        val (conversationId, request) = alice.startHandshake(bob.publicKey, "Alice", sharedSeed)
        val duplicateRequest = request.copy(id = UUID.randomUUID().toString())

        bob.receive(request)
        bob.receive(duplicateRequest)

        assertEquals(1, bob.conversationCount())
        assertTrue(bob.hasConversation(conversationId))
    }

    @Test
    fun groupMessagesRouteOnlyToDevicesWithMatchingGroupKey() {
        val alice = SimulatedAnonymousClient("Alice", encryptionService, codec)
        val bob = SimulatedAnonymousClient("Bob", encryptionService, codec)
        val eve = SimulatedAnonymousClient("Eve", encryptionService, codec)

        val groupKey = GroupKeyRecord(
            groupLocalId = "group_local_1",
            localGroupName = "Group A",
            poolId = AnonymousPools.groupPoolFor("group_local_1"),
            groupKey = codec.generateGroupKey(),
            createdAt = 1L,
            updatedAt = 1L
        )
        val otherGroupKey = GroupKeyRecord(
            groupLocalId = "group_local_2",
            localGroupName = "Group B",
            poolId = groupKey.poolId,
            groupKey = codec.generateGroupKey(),
            createdAt = 1L,
            updatedAt = 1L
        )

        alice.installGroupKey(groupKey)
        bob.installGroupKey(groupKey)
        eve.installGroupKey(otherGroupKey)

        val envelope = alice.sendGroupText(groupKey.groupLocalId, "hello group")
        bob.receive(envelope)
        eve.receive(envelope)

        assertEquals(listOf("hello group"), bob.groupTexts(groupKey.groupLocalId))
        assertTrue(eve.groupTexts(otherGroupKey.groupLocalId).isEmpty())
    }

    @Test
    fun duplicateEnvelopeAndOldProcessedMessagesAreIgnored() {
        val alice = SimulatedAnonymousClient("Alice", encryptionService, codec)
        val bob = SimulatedAnonymousClient("Bob", encryptionService, codec)
        val (conversationId, request) = alice.startHandshake(bob.publicKey, "Alice")
        val accept = bob.receive(request).single()
        alice.receive(accept)

        val envelope = alice.sendPrivateText(conversationId, "once")
        bob.receive(envelope)
        bob.receive(envelope)

        assertEquals(listOf("once"), bob.privateTexts(conversationId))
        assertEquals(1, bob.privateTexts(conversationId).size)
    }

    @Test
    fun brokenPayloadAndUnknownVersionAreSafelyIgnored() {
        val alice = SimulatedAnonymousClient("Alice", encryptionService, codec)
        val bob = SimulatedAnonymousClient("Bob", encryptionService, codec)
        val (conversationId, request) = alice.startHandshake(bob.publicKey, "Alice")
        val accept = bob.receive(request).single()
        alice.receive(accept)

        val unknownVersionEnvelope = alice.sendPrivateText(
            conversationId = conversationId,
            text = "ignored",
            protocolVersion = 999
        )
        val brokenEnvelope = alice.sendPrivateText(conversationId, "broken").copy(
            id = UUID.randomUUID().toString(),
            ciphertext = "%%%not-base64%%%"
        )

        bob.receive(unknownVersionEnvelope)
        bob.receive(brokenEnvelope)

        assertTrue(bob.privateTexts(conversationId).isEmpty())
    }

    @Test
    fun noisyPoolKeepsOnlyRelevantMessagesWithReasonableProcessingTime() {
        val alice = SimulatedAnonymousClient("Alice", encryptionService, codec)
        val bob = SimulatedAnonymousClient("Bob", encryptionService, codec)
        val eve = SimulatedAnonymousClient("Eve", encryptionService, codec)
        val mallory = SimulatedAnonymousClient("Mallory", encryptionService, codec)

        val (conversationId, request) = alice.startHandshake(bob.publicKey, "Alice")
        alice.receive(bob.receive(request).single())

        val foreignGroupKey = GroupKeyRecord(
            groupLocalId = "foreign_group",
            localGroupName = "Noise",
            poolId = AnonymousPools.GROUP_POOLS.first(),
            groupKey = codec.generateGroupKey(),
            createdAt = 1L,
            updatedAt = 1L
        )
        eve.installGroupKey(foreignGroupKey)
        mallory.installGroupKey(foreignGroupKey)

        val noise = buildList {
            repeat(60) { index ->
                add(eve.sendGroupText(foreignGroupKey.groupLocalId, "noise-$index"))
            }
            repeat(40) { index ->
                add(mallory.sendHandshakeOnly(eve.publicKey, "m-$index"))
            }
        }
        val targetEnvelopes = listOf(
            alice.sendPrivateText(conversationId, "msg-1"),
            alice.sendPrivateText(conversationId, "msg-2"),
            alice.sendPrivateText(conversationId, "msg-3")
        )

        val elapsedMs = measureTimeMillis {
            (noise + targetEnvelopes).shuffled().forEach { envelope ->
                bob.receive(envelope)
            }
        }

        assertEquals(listOf("msg-1", "msg-2", "msg-3").sorted(), bob.privateTexts(conversationId).sorted())
        assertTrue("Processing took too long: ${elapsedMs}ms", elapsedMs < 15_000L)
    }

    private data class SimConversation(
        val conversationId: String,
        val conversationSeed: String,
        val peerPublicKey: String,
        val poolId: String,
        val keyMaterial: ByteArray,
        var status: SessionStatus
    )

    private class SimulatedAnonymousClient(
        private val alias: String,
        private val encryptionService: EncryptionService,
        private val codec: AnonymousMessageCodec
    ) {
        private val identity = encryptionService.generateKeyPair()
        val publicKey: String = encryptionService.exportPublicKey(identity.public)
        val privateKey: String = encryptionService.exportPrivateKey(identity.private)

        private val conversations = linkedMapOf<String, SimConversation>()
        private val processedEnvelopeIds = linkedSetOf<String>()
        private val groupKeys = linkedMapOf<String, GroupKeyRecord>()
        private val privateMessages = linkedMapOf<String, MutableList<String>>()
        private val groupMessages = linkedMapOf<String, MutableList<String>>()

        fun startHandshake(
            peerPublicKey: String,
            localAlias: String?,
            conversationSeed: String = codec.generateConversationSeed()
        ): Pair<String, AnonymousEnvelope> {
            val conversationId = codec.buildConversationId(conversationSeed)
            val keyMaterial = codec.deriveConversationKeyMaterial(
                encryptionService.deriveSharedSecret(identity.private, encryptionService.importPublicKey(peerPublicKey)),
                conversationSeed
            )
            conversations[conversationId] = SimConversation(
                conversationId = conversationId,
                conversationSeed = conversationSeed,
                peerPublicKey = peerPublicKey,
                poolId = AnonymousPools.conversationPoolFor(conversationSeed),
                keyMaterial = keyMaterial,
                status = SessionStatus.PENDING
            )
            val envelope = codec.createAsymmetricEnvelope(
                poolId = AnonymousPools.inboundPrivatePoolFor(peerPublicKey),
                recipientPublicKey = peerPublicKey,
                payload = jsonStringOf(
                    "type" to "HANDSHAKE_REQUEST",
                    "protocolVersion" to AnonymousMessageCodec.PROTOCOL_VERSION,
                    "conversationSeed" to conversationSeed,
                    "initiatorPublicKey" to publicKey,
                    "initiatorAlias" to localAlias,
                    "createdAt" to System.currentTimeMillis()
                )
            ).withId()
            return conversationId to envelope
        }

        fun sendHandshakeOnly(peerPublicKey: String, suffix: String): AnonymousEnvelope {
            return startHandshake(peerPublicKey, "$alias-$suffix").second
        }

        fun sendPrivateText(
            conversationId: String,
            text: String,
            protocolVersion: Int = AnonymousMessageCodec.PROTOCOL_VERSION
        ): AnonymousEnvelope {
            val conversation = conversations.getValue(conversationId)
            conversation.status = SessionStatus.ACTIVE
            return codec.createSymmetricEnvelope(
                poolId = conversation.poolId,
                keyMaterial = conversation.keyMaterial,
                payload = jsonStringOf(
                    "type" to "PRIVATE_TEXT",
                    "protocolVersion" to protocolVersion,
                    "conversationId" to conversationId,
                    "text" to text,
                    "sentAt" to System.currentTimeMillis(),
                    "senderAlias" to alias
                )
            ).withId()
        }

        fun installGroupKey(record: GroupKeyRecord) {
            groupKeys[record.groupLocalId] = record
        }

        fun sendGroupText(groupLocalId: String, text: String): AnonymousEnvelope {
            val record = groupKeys.getValue(groupLocalId)
            return codec.createSymmetricEnvelope(
                poolId = record.poolId,
                keyMaterial = encryptionService.decodeBase64(record.groupKey),
                payload = jsonStringOf(
                    "type" to "GROUP_TEXT",
                    "protocolVersion" to AnonymousMessageCodec.PROTOCOL_VERSION,
                    "groupLocalIdOrRoutingHint" to groupLocalId,
                    "text" to text,
                    "sentAt" to System.currentTimeMillis(),
                    "senderAlias" to alias
                )
            ).withId()
        }

        fun receive(envelope: AnonymousEnvelope): List<AnonymousEnvelope> {
            if (!processedEnvelopeIds.add(envelope.id)) return emptyList()

            try {
                val handshake = codec.tryDecryptAsymmetricEnvelopeRaw(envelope, privateKey)?.let(TestJson::parseObject)
                if (handshake != null && TestJson.int(handshake, "protocolVersion", -1) == AnonymousMessageCodec.PROTOCOL_VERSION) {
                    when (TestJson.string(handshake, "type")) {
                        "HANDSHAKE_REQUEST" -> return listOf(acceptHandshake(handshake))
                        "HANDSHAKE_ACCEPT" -> {
                            applyAccept(handshake)
                            return emptyList()
                        }
                    }
                }

                for (conversation in conversations.values.filter { it.poolId == envelope.poolId }) {
                    val payload = codec.tryDecryptSymmetricEnvelopeRaw(envelope, conversation.keyMaterial)?.let(TestJson::parseObject)
                        ?: continue
                    if (TestJson.int(payload, "protocolVersion", -1) != AnonymousMessageCodec.PROTOCOL_VERSION) continue
                    if (TestJson.string(payload, "conversationId") != conversation.conversationId) continue
                    if (TestJson.string(payload, "type") == "PRIVATE_TEXT") {
                        conversation.status = SessionStatus.ACTIVE
                        privateMessages.getOrPut(conversation.conversationId) { mutableListOf() }
                            .add(TestJson.string(payload, "text").orEmpty())
                        return emptyList()
                    }
                }

                for (group in groupKeys.values.filter { it.poolId == envelope.poolId }) {
                    val payload = codec.tryDecryptSymmetricEnvelopeRaw(envelope, encryptionService.decodeBase64(group.groupKey))
                        ?.let(TestJson::parseObject) ?: continue
                    if (TestJson.int(payload, "protocolVersion", -1) != AnonymousMessageCodec.PROTOCOL_VERSION) continue
                    if (TestJson.string(payload, "type") != "GROUP_TEXT") continue
                    if (TestJson.string(payload, "groupLocalIdOrRoutingHint") != group.groupLocalId) continue
                    groupMessages.getOrPut(group.groupLocalId) { mutableListOf() }
                        .add(TestJson.string(payload, "text").orEmpty())
                    return emptyList()
                }
            } catch (_: Exception) {
                return emptyList()
            }

            return emptyList()
        }

        fun hasConversation(conversationId: String): Boolean = conversations.containsKey(conversationId)

        fun conversation(conversationId: String): SimConversation? = conversations[conversationId]

        fun conversationCount(): Int = conversations.size

        fun sessionStatus(conversationId: String): SessionStatus? = conversations[conversationId]?.status

        fun privateTexts(conversationId: String): List<String> = privateMessages[conversationId].orEmpty()

        fun groupTexts(groupLocalId: String): List<String> = groupMessages[groupLocalId].orEmpty()

        private fun acceptHandshake(handshake: Map<String, Any?>): AnonymousEnvelope {
            val conversationSeed = TestJson.string(handshake, "conversationSeed").orEmpty()
            val initiatorPublicKey = TestJson.string(handshake, "initiatorPublicKey").orEmpty()
            val conversationId = codec.buildConversationId(conversationSeed)
            val keyMaterial = codec.deriveConversationKeyMaterial(
                encryptionService.deriveSharedSecret(identity.private, encryptionService.importPublicKey(initiatorPublicKey)),
                conversationSeed
            )
            conversations[conversationId] = SimConversation(
                conversationId = conversationId,
                conversationSeed = conversationSeed,
                peerPublicKey = initiatorPublicKey,
                poolId = AnonymousPools.conversationPoolFor(conversationSeed),
                keyMaterial = keyMaterial,
                status = SessionStatus.ACCEPTED
            )
            return codec.createAsymmetricEnvelope(
                poolId = AnonymousPools.inboundPrivatePoolFor(initiatorPublicKey),
                recipientPublicKey = initiatorPublicKey,
                payload = jsonStringOf(
                    "type" to "HANDSHAKE_ACCEPT",
                    "protocolVersion" to AnonymousMessageCodec.PROTOCOL_VERSION,
                    "conversationSeed" to conversationSeed,
                    "responderPublicKey" to publicKey,
                    "acceptedAt" to System.currentTimeMillis()
                )
            ).withId()
        }

        private fun applyAccept(handshake: Map<String, Any?>) {
            val conversationSeed = TestJson.string(handshake, "conversationSeed").orEmpty()
            val responderPublicKey = TestJson.string(handshake, "responderPublicKey").orEmpty()
            val conversationId = codec.buildConversationId(conversationSeed)
            val existing = conversations[conversationId]
            val keyMaterial = codec.deriveConversationKeyMaterial(
                encryptionService.deriveSharedSecret(identity.private, encryptionService.importPublicKey(responderPublicKey)),
                conversationSeed
            )
            conversations[conversationId] = SimConversation(
                conversationId = conversationId,
                conversationSeed = conversationSeed,
                peerPublicKey = responderPublicKey,
                poolId = existing?.poolId ?: AnonymousPools.conversationPoolFor(conversationSeed),
                keyMaterial = keyMaterial,
                status = SessionStatus.ACCEPTED
            )
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
}

