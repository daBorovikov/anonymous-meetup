package com.example.anonymousmeetup.data.security

import com.example.anonymousmeetup.data.model.AnonymousEnvelope
import com.example.anonymousmeetup.data.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PrivateInviteFlowTest {

    private lateinit var encryptionService: EncryptionService
    private lateinit var codec: AnonymousMessageCodec

    @Before
    fun setUp() {
        encryptionService = EncryptionService()
        codec = AnonymousMessageCodec(encryptionService)
    }

    @Test
    fun inviteStaysPendingUntilRecipientAccepts() {
        val alice = InviteClient(encryptionService, codec)
        val bob = InviteClient(encryptionService, codec)

        val (conversationId, request) = alice.startInvite(bob.publicKey)
        bob.receive(request)

        assertEquals(SessionStatus.PENDING, alice.status(conversationId))
        assertEquals(SessionStatus.PENDING, bob.status(conversationId))

        val accept = bob.accept(conversationId)
        alice.receive(accept)

        assertEquals(SessionStatus.ACCEPTED, alice.status(conversationId))
        assertEquals(SessionStatus.ACCEPTED, bob.status(conversationId))
    }

    @Test
    fun rejectMarksConversationRejectedForBothSides() {
        val alice = InviteClient(encryptionService, codec)
        val bob = InviteClient(encryptionService, codec)

        val (conversationId, request) = alice.startInvite(bob.publicKey)
        bob.receive(request)
        val reject = bob.reject(conversationId)
        alice.receive(reject)

        assertEquals(SessionStatus.REJECTED, alice.status(conversationId))
        assertEquals(SessionStatus.REJECTED, bob.status(conversationId))
    }

    private data class ConversationState(
        val conversationSeed: String,
        val peerPublicKey: String,
        val status: SessionStatus
    )

    private class InviteClient(
        private val encryptionService: EncryptionService,
        private val codec: AnonymousMessageCodec
    ) {
        private val identity = encryptionService.generateKeyPair()
        private val conversations = linkedMapOf<String, ConversationState>()

        val publicKey: String = encryptionService.exportPublicKey(identity.public)
        private val privateKey: String = encryptionService.exportPrivateKey(identity.private)

        fun startInvite(peerPublicKey: String): Pair<String, AnonymousEnvelope> {
            val seed = codec.generateConversationSeed()
            val conversationId = codec.buildConversationId(seed)
            conversations[conversationId] = ConversationState(
                conversationSeed = seed,
                peerPublicKey = peerPublicKey,
                status = SessionStatus.PENDING
            )
            val envelope = codec.createAsymmetricEnvelope(
                poolId = AnonymousPools.inboundPrivatePoolFor(peerPublicKey),
                recipientPublicKey = peerPublicKey,
                payload = jsonPayload(
                    "HANDSHAKE_REQUEST",
                    seed,
                    extraField = "initiatorPublicKey",
                    extraValue = publicKey
                )
            )
            return conversationId to envelope
        }

        fun receive(envelope: AnonymousEnvelope) {
            val payload = codec.tryDecryptAsymmetricEnvelopeRaw(envelope, privateKey) ?: return
            val json = TestJson.parseObject(payload)
            val type = TestJson.string(json, "type") ?: return
            val seed = TestJson.string(json, "conversationSeed").orEmpty()
            val conversationId = codec.buildConversationId(seed)
            when (type) {
                "HANDSHAKE_REQUEST" -> {
                    val initiatorPublicKey = TestJson.string(json, "initiatorPublicKey").orEmpty()
                    conversations[conversationId] = ConversationState(seed, initiatorPublicKey, SessionStatus.PENDING)
                }
                "HANDSHAKE_ACCEPT" -> {
                    val responderPublicKey = TestJson.string(json, "responderPublicKey").orEmpty()
                    conversations[conversationId] = ConversationState(seed, responderPublicKey, SessionStatus.ACCEPTED)
                }
                "HANDSHAKE_REJECT" -> {
                    val responderPublicKey = TestJson.string(json, "responderPublicKey").orEmpty()
                    conversations[conversationId] = ConversationState(seed, responderPublicKey, SessionStatus.REJECTED)
                }
            }
        }

        fun accept(conversationId: String): AnonymousEnvelope {
            val state = conversations.getValue(conversationId)
            conversations[conversationId] = state.copy(status = SessionStatus.ACCEPTED)
            return codec.createAsymmetricEnvelope(
                poolId = AnonymousPools.inboundPrivatePoolFor(state.peerPublicKey),
                recipientPublicKey = state.peerPublicKey,
                payload = jsonPayload(
                    "HANDSHAKE_ACCEPT",
                    state.conversationSeed,
                    extraField = "responderPublicKey",
                    extraValue = publicKey
                )
            )
        }

        fun reject(conversationId: String): AnonymousEnvelope {
            val state = conversations.getValue(conversationId)
            conversations[conversationId] = state.copy(status = SessionStatus.REJECTED)
            return codec.createAsymmetricEnvelope(
                poolId = AnonymousPools.inboundPrivatePoolFor(state.peerPublicKey),
                recipientPublicKey = state.peerPublicKey,
                payload = jsonPayload(
                    "HANDSHAKE_REJECT",
                    state.conversationSeed,
                    extraField = "responderPublicKey",
                    extraValue = publicKey
                )
            )
        }

        fun status(conversationId: String): SessionStatus? = conversations[conversationId]?.status

        private fun jsonPayload(
            type: String,
            seed: String,
            extraField: String,
            extraValue: String
        ): String {
            return """
                {"type":"$type","protocolVersion":${AnonymousMessageCodec.PROTOCOL_VERSION},"conversationSeed":"$seed","$extraField":"$extraValue"}
            """.trimIndent()
        }
    }
}
