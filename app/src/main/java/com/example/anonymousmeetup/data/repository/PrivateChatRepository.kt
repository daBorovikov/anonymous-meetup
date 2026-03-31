package com.example.anonymousmeetup.data.repository

import com.example.anonymousmeetup.data.debug.DebugTraceLogger
import com.example.anonymousmeetup.data.local.ConversationSecretStore
import com.example.anonymousmeetup.data.local.LocalConversationStore
import com.example.anonymousmeetup.data.local.LocalMessageStore
import com.example.anonymousmeetup.data.local.ProcessedEnvelopeStore
import com.example.anonymousmeetup.data.model.LocalConversation
import com.example.anonymousmeetup.data.model.LocalMessageRecord
import com.example.anonymousmeetup.data.model.LocalMessageType
import com.example.anonymousmeetup.data.model.LocationPayload
import com.example.anonymousmeetup.data.model.MessageDirection
import com.example.anonymousmeetup.data.model.ProcessedEnvelopeOutcome
import com.example.anonymousmeetup.data.model.ProcessedEnvelopeRecord
import com.example.anonymousmeetup.data.model.SessionStatus
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.remote.FirebaseService
import com.example.anonymousmeetup.data.security.AnonymousMessageCodec
import com.example.anonymousmeetup.data.security.AnonymousPools
import com.example.anonymousmeetup.data.security.EncryptionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivateChatRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val userPreferences: UserPreferences,
    private val userRepository: UserRepository,
    private val encryptionService: EncryptionService,
    private val anonymousMessageCodec: AnonymousMessageCodec,
    private val localConversationStore: LocalConversationStore,
    private val localMessageStore: LocalMessageStore,
    private val processedEnvelopeStore: ProcessedEnvelopeStore,
    private val conversationSecretStore: ConversationSecretStore,
    private val debugTraceLogger: DebugTraceLogger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenersStarted = false

    fun observeConversations(): Flow<List<LocalConversation>> {
        ensureListenersStarted()
        return localConversationStore.observeConversations()
    }

    fun observeConversation(conversationId: String): Flow<LocalConversation?> {
        ensureListenersStarted()
        return localConversationStore.observeConversation(conversationId)
    }

    fun observeMessages(conversationId: String): Flow<List<LocalMessageRecord>> {
        ensureListenersStarted()
        return localMessageStore.observeMessages(conversationId)
    }

    suspend fun startPrivateChat(peerPublicKey: String, localAlias: String?): Result<String> {
        ensureListenersStarted()
        return runCatching {
            val now = System.currentTimeMillis()
            val existing = localConversationStore.getConversationByPeerKey(peerPublicKey)
            if (existing != null && existing.sessionStatus != SessionStatus.FAILED && existing.sessionStatus != SessionStatus.REJECTED) {
                val mergedAlias = localAlias?.ifBlank { null } ?: existing.localAlias
                if (mergedAlias != existing.localAlias) {
                    localConversationStore.upsertConversation(existing.copy(localAlias = mergedAlias, updatedAt = now))
                }
                debugTraceLogger.debug(TAG, "startPrivateChat reused conversationId=${existing.conversationId}")
                return@runCatching existing.conversationId
            }

            val identity = userRepository.ensureIdentityKeys()
            val peerPublic = encryptionService.importPublicKey(peerPublicKey)
            val myPrivate = encryptionService.importPrivateKey(identity.privateKey)
            val conversationSeed = anonymousMessageCodec.generateConversationSeed()
            val conversationId = anonymousMessageCodec.buildConversationId(conversationSeed)
            val conversationPoolId = AnonymousPools.conversationPoolFor(conversationSeed)
            val sharedSecret = encryptionService.deriveSharedSecret(myPrivate, peerPublic)
            val keyMaterial = anonymousMessageCodec.deriveConversationKeyMaterial(sharedSecret, conversationSeed)
            val secretRef = "secret_$conversationId"
            conversationSecretStore.saveSecret(secretRef, keyMaterial)

            localConversationStore.upsertConversation(
                LocalConversation(
                    conversationId = conversationId,
                    peerPublicKey = peerPublicKey,
                    localAlias = localAlias?.ifBlank { null },
                    sessionStatus = SessionStatus.PENDING,
                    sharedSecretRef = secretRef,
                    poolId = conversationPoolId,
                    conversationSeed = conversationSeed,
                    createdAt = now,
                    updatedAt = now,
                    isInitiator = true
                )
            )

            val payload = JSONObject()
                .put("type", "HANDSHAKE_REQUEST")
                .put("protocolVersion", AnonymousMessageCodec.PROTOCOL_VERSION)
                .put("conversationSeed", conversationSeed)
                .put("initiatorPublicKey", identity.publicKey)
                .put("initiatorAlias", userPreferences.getNickname().orEmpty().ifBlank { null })
                .put("createdAt", now)

            val requestPoolId = AnonymousPools.inboundPrivatePoolFor(peerPublicKey)
            val envelope = anonymousMessageCodec.createAsymmetricEnvelope(
                poolId = requestPoolId,
                recipientPublicKey = peerPublicKey,
                payloadJson = payload
            )
            debugTraceLogger.debug(TAG, "startPrivateChat conversationId=$conversationId requestPoolId=$requestPoolId")
            firebaseService.sendAnonymousEnvelope(envelope)
            appendSystemMessage(
                targetId = conversationId,
                direction = MessageDirection.OUTGOING,
                text = "Приглашение в анонимный чат отправлено",
                sourceEnvelopeId = null
            )
            conversationId
        }
    }

    suspend fun acceptConversationInvite(conversationId: String) {
        ensureListenersStarted()
        val conversation = localConversationStore.getConversation(conversationId)
            ?: throw IllegalStateException("Приватная беседа не найдена")
        require(!conversation.isInitiator) { "Нельзя принять своё собственное приглашение" }
        require(conversation.sessionStatus == SessionStatus.PENDING) { "Приглашение уже обработано" }

        val identity = userRepository.ensureIdentityKeys()
        val now = System.currentTimeMillis()
        localConversationStore.updateStatus(
            conversationId = conversationId,
            status = SessionStatus.ACCEPTED,
            updatedAt = now,
            acceptedAt = now
        )
        appendSystemMessage(
            targetId = conversationId,
            direction = MessageDirection.OUTGOING,
            text = "Вы приняли приглашение в анонимный чат",
            sourceEnvelopeId = null
        )

        val acceptPayload = JSONObject()
            .put("type", "HANDSHAKE_ACCEPT")
            .put("protocolVersion", AnonymousMessageCodec.PROTOCOL_VERSION)
            .put("conversationSeed", conversation.conversationSeed)
            .put("responderPublicKey", identity.publicKey)
            .put("acceptedAt", now)

        val responseEnvelope = anonymousMessageCodec.createAsymmetricEnvelope(
            poolId = AnonymousPools.inboundPrivatePoolFor(conversation.peerPublicKey),
            recipientPublicKey = conversation.peerPublicKey,
            payloadJson = acceptPayload
        )
        debugTraceLogger.debug(TAG, "acceptConversationInvite conversationId=$conversationId")
        firebaseService.sendAnonymousEnvelope(responseEnvelope)
    }

    suspend fun rejectConversationInvite(conversationId: String) {
        ensureListenersStarted()
        val conversation = localConversationStore.getConversation(conversationId)
            ?: throw IllegalStateException("Приватная беседа не найдена")
        require(!conversation.isInitiator) { "Нельзя отклонить своё собственное приглашение" }
        require(conversation.sessionStatus == SessionStatus.PENDING) { "Приглашение уже обработано" }

        val identity = userRepository.ensureIdentityKeys()
        val now = System.currentTimeMillis()
        localConversationStore.updateStatus(
            conversationId = conversationId,
            status = SessionStatus.REJECTED,
            updatedAt = now
        )
        appendSystemMessage(
            targetId = conversationId,
            direction = MessageDirection.OUTGOING,
            text = "Вы отклонили приглашение в анонимный чат",
            sourceEnvelopeId = null
        )

        val rejectPayload = JSONObject()
            .put("type", "HANDSHAKE_REJECT")
            .put("protocolVersion", AnonymousMessageCodec.PROTOCOL_VERSION)
            .put("conversationSeed", conversation.conversationSeed)
            .put("responderPublicKey", identity.publicKey)
            .put("rejectedAt", now)

        val responseEnvelope = anonymousMessageCodec.createAsymmetricEnvelope(
            poolId = AnonymousPools.inboundPrivatePoolFor(conversation.peerPublicKey),
            recipientPublicKey = conversation.peerPublicKey,
            payloadJson = rejectPayload
        )
        debugTraceLogger.debug(TAG, "rejectConversationInvite conversationId=$conversationId")
        firebaseService.sendAnonymousEnvelope(responseEnvelope)
    }

    suspend fun sendPrivateText(conversationId: String, text: String) {
        ensureListenersStarted()
        val conversation = localConversationStore.getConversation(conversationId)
            ?: throw IllegalStateException("Приватная беседа не найдена")
        require(conversation.canSendEncryptedMessages()) { "Приглашение ещё не принято" }
        val keyMaterial = conversationSecretStore.getSecret(conversation.sharedSecretRef)
            ?: throw IllegalStateException("Секрет беседы не найден")
        val sentAt = System.currentTimeMillis()
        val payload = JSONObject()
            .put("type", "PRIVATE_TEXT")
            .put("protocolVersion", AnonymousMessageCodec.PROTOCOL_VERSION)
            .put("conversationId", conversation.conversationId)
            .put("text", text)
            .put("sentAt", sentAt)
            .put("senderAlias", userPreferences.getNickname().orEmpty().ifBlank { null })

        debugTraceLogger.debug(TAG, "sendPrivateText conversationId=$conversationId poolId=${conversation.poolId}")
        val envelope = anonymousMessageCodec.createSymmetricEnvelope(
            poolId = conversation.poolId,
            keyMaterial = keyMaterial,
            payloadJson = payload
        )
        firebaseService.sendAnonymousEnvelope(envelope)
        localMessageStore.appendMessage(
            LocalMessageRecord(
                localMessageId = anonymousMessageCodec.newLocalMessageId(),
                conversationIdOrGroupLocalId = conversationId,
                direction = MessageDirection.OUTGOING,
                type = LocalMessageType.PRIVATE_TEXT,
                text = text,
                rawPayloadJson = payload.toString(),
                timestamp = sentAt,
                sourceEnvelopeId = null,
                isRead = true
            )
        )
        localConversationStore.updateStatus(
            conversationId = conversationId,
            status = SessionStatus.ACTIVE,
            updatedAt = sentAt,
            acceptedAt = conversation.acceptedAt ?: sentAt,
            lastMessageAt = sentAt
        )
    }

    suspend fun sendLocation(conversationId: String, locationPayload: LocationPayload) {
        ensureListenersStarted()
        val conversation = localConversationStore.getConversation(conversationId)
            ?: throw IllegalStateException("Приватная беседа не найдена")
        require(conversation.canSendEncryptedMessages()) { "Приглашение ещё не принято" }
        val keyMaterial = conversationSecretStore.getSecret(conversation.sharedSecretRef)
            ?: throw IllegalStateException("Секрет беседы не найден")
        val payload = JSONObject()
            .put("type", "LOCATION")
            .put("protocolVersion", AnonymousMessageCodec.PROTOCOL_VERSION)
            .put("conversationId", conversation.conversationId)
            .put("latitude", locationPayload.latitude)
            .put("longitude", locationPayload.longitude)
            .put("sentAt", locationPayload.sentAt)
            .put("senderAlias", locationPayload.displayName)

        debugTraceLogger.debug(TAG, "sendLocation conversationId=$conversationId poolId=${conversation.poolId}")
        val envelope = anonymousMessageCodec.createSymmetricEnvelope(
            poolId = conversation.poolId,
            keyMaterial = keyMaterial,
            payloadJson = payload
        )
        firebaseService.sendAnonymousEnvelope(envelope)
        localMessageStore.appendMessage(
            LocalMessageRecord(
                localMessageId = anonymousMessageCodec.newLocalMessageId(),
                conversationIdOrGroupLocalId = conversationId,
                direction = MessageDirection.OUTGOING,
                type = LocalMessageType.LOCATION,
                text = "${locationPayload.latitude}, ${locationPayload.longitude}",
                rawPayloadJson = payload.toString(),
                timestamp = locationPayload.sentAt,
                sourceEnvelopeId = null,
                isRead = true
            )
        )
        localConversationStore.updateStatus(
            conversationId = conversationId,
            status = SessionStatus.ACTIVE,
            updatedAt = locationPayload.sentAt,
            acceptedAt = conversation.acceptedAt ?: locationPayload.sentAt,
            lastMessageAt = locationPayload.sentAt
        )
    }

    suspend fun updateLocalAlias(conversationId: String, alias: String?) {
        val conversation = localConversationStore.getConversation(conversationId) ?: return
        localConversationStore.upsertConversation(
            conversation.copy(localAlias = alias?.ifBlank { null }, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun clearConversationHistory(conversationId: String) {
        localMessageStore.clearHistory(conversationId)
    }

    private fun ensureListenersStarted() {
        if (listenersStarted) return
        listenersStarted = true
        AnonymousPools.PRIVATE_POOLS.forEach { poolId ->
            scope.launch {
                val since = processedEnvelopeStore.getLastSeen(poolId)
                firebaseService.listenAnonymousPool(poolId, since).collect { envelopes ->
                    for (envelope in envelopes.sortedBy { it.timestamp }) {
                        if (processedEnvelopeStore.isProcessed(envelope.id)) {
                            processedEnvelopeStore.updateLastSeen(poolId, envelope.timestamp)
                            continue
                        }
                        val outcome = processEnvelope(envelope)
                        processedEnvelopeStore.markProcessed(
                            ProcessedEnvelopeRecord(
                                envelopeId = envelope.id,
                                poolId = envelope.poolId,
                                processedAt = System.currentTimeMillis(),
                                outcome = outcome
                            )
                        )
                        processedEnvelopeStore.updateLastSeen(poolId, envelope.timestamp)
                    }
                }
            }
        }
    }

    private suspend fun processEnvelope(envelope: com.example.anonymousmeetup.data.model.AnonymousEnvelope): ProcessedEnvelopeOutcome {
        return try {
            if (handleHandshakeEnvelope(envelope)) {
                debugTraceLogger.debug(TAG, "processEnvelope routed handshake id=${envelope.id}")
                ProcessedEnvelopeOutcome.ROUTED
            } else if (handleConversationEnvelope(envelope)) {
                debugTraceLogger.debug(TAG, "processEnvelope routed conversation id=${envelope.id}")
                ProcessedEnvelopeOutcome.ROUTED
            } else {
                debugTraceLogger.debug(TAG, "processEnvelope ignored id=${envelope.id} poolId=${envelope.poolId}")
                ProcessedEnvelopeOutcome.IGNORED
            }
        } catch (e: Exception) {
            debugTraceLogger.error(TAG, "processEnvelope failed id=${envelope.id}", e)
            ProcessedEnvelopeOutcome.FAILED
        }
    }

    private suspend fun handleHandshakeEnvelope(envelope: com.example.anonymousmeetup.data.model.AnonymousEnvelope): Boolean {
        val identityPrivateKey = userRepository.getIdentityPrivateKey()
        val json = anonymousMessageCodec.tryDecryptAsymmetricEnvelope(envelope, identityPrivateKey) ?: return false
        if (json.optInt("protocolVersion", -1) != AnonymousMessageCodec.PROTOCOL_VERSION) return false
        return when (json.optString("type")) {
            "HANDSHAKE_REQUEST" -> {
                debugTraceLogger.debug(TAG, "handleHandshakeEnvelope request id=${envelope.id}")
                stageIncomingHandshake(json, envelope.id)
                true
            }
            "HANDSHAKE_ACCEPT" -> {
                debugTraceLogger.debug(TAG, "handleHandshakeEnvelope accept id=${envelope.id}")
                applyHandshakeAccept(json, envelope.id)
                true
            }
            "HANDSHAKE_REJECT" -> {
                debugTraceLogger.debug(TAG, "handleHandshakeEnvelope reject id=${envelope.id}")
                applyHandshakeReject(json, envelope.id)
                true
            }
            else -> false
        }
    }

    private suspend fun stageIncomingHandshake(json: JSONObject, envelopeId: String) {
        val conversationSeed = json.optString("conversationSeed")
        val initiatorPublicKey = json.optString("initiatorPublicKey")
        if (conversationSeed.isBlank() || initiatorPublicKey.isBlank()) return

        val identity = userRepository.ensureIdentityKeys()
        val conversationId = anonymousMessageCodec.buildConversationId(conversationSeed)
        val conversationPoolId = AnonymousPools.conversationPoolFor(conversationSeed)
        val sharedSecret = encryptionService.deriveSharedSecret(
            encryptionService.importPrivateKey(identity.privateKey),
            encryptionService.importPublicKey(initiatorPublicKey)
        )
        val keyMaterial = anonymousMessageCodec.deriveConversationKeyMaterial(sharedSecret, conversationSeed)
        val secretRef = "secret_$conversationId"
        conversationSecretStore.saveSecret(secretRef, keyMaterial)

        val existing = localConversationStore.getConversation(conversationId)
        if (existing != null && existing.sessionStatus == SessionStatus.ACTIVE) {
            return
        }

        val now = System.currentTimeMillis()
        val createdAt = json.optLong("createdAt", now)
        val aliasFromCiphertext = json.optString("initiatorAlias").ifBlank { null }
        val nextStatus = when (existing?.sessionStatus) {
            SessionStatus.ACCEPTED, SessionStatus.ACTIVE -> existing.sessionStatus
            else -> SessionStatus.PENDING
        }

        localConversationStore.upsertConversation(
            LocalConversation(
                conversationId = conversationId,
                peerPublicKey = initiatorPublicKey,
                localAlias = existing?.localAlias ?: aliasFromCiphertext,
                sessionStatus = nextStatus,
                sharedSecretRef = secretRef,
                poolId = conversationPoolId,
                conversationSeed = conversationSeed,
                createdAt = existing?.createdAt ?: createdAt,
                updatedAt = now,
                acceptedAt = existing?.acceptedAt,
                lastMessageAt = existing?.lastMessageAt,
                isInitiator = false
            )
        )

        if (existing == null || existing.sessionStatus == SessionStatus.REJECTED || existing.sessionStatus == SessionStatus.FAILED) {
            appendSystemMessage(
                targetId = conversationId,
                direction = MessageDirection.INCOMING,
                text = "Вам пришло приглашение в анонимный чат",
                sourceEnvelopeId = envelopeId
            )
        }
    }

    private suspend fun applyHandshakeAccept(json: JSONObject, envelopeId: String) {
        val conversationSeed = json.optString("conversationSeed")
        val responderPublicKey = json.optString("responderPublicKey")
        if (conversationSeed.isBlank() || responderPublicKey.isBlank()) return

        val conversationId = anonymousMessageCodec.buildConversationId(conversationSeed)
        val existing = localConversationStore.getConversation(conversationId)
        val identity = userRepository.ensureIdentityKeys()
        val sharedSecret = encryptionService.deriveSharedSecret(
            encryptionService.importPrivateKey(identity.privateKey),
            encryptionService.importPublicKey(responderPublicKey)
        )
        val keyMaterial = anonymousMessageCodec.deriveConversationKeyMaterial(sharedSecret, conversationSeed)
        val secretRef = existing?.sharedSecretRef ?: "secret_$conversationId"
        conversationSecretStore.saveSecret(secretRef, keyMaterial)

        val now = json.optLong("acceptedAt", System.currentTimeMillis())
        localConversationStore.upsertConversation(
            LocalConversation(
                conversationId = conversationId,
                peerPublicKey = responderPublicKey,
                localAlias = existing?.localAlias,
                sessionStatus = SessionStatus.ACCEPTED,
                sharedSecretRef = secretRef,
                poolId = existing?.poolId ?: AnonymousPools.conversationPoolFor(conversationSeed),
                conversationSeed = conversationSeed,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                acceptedAt = now,
                lastMessageAt = existing?.lastMessageAt,
                isInitiator = existing?.isInitiator ?: true
            )
        )
        appendSystemMessage(
            targetId = conversationId,
            direction = MessageDirection.INCOMING,
            text = "Собеседник принял приглашение",
            sourceEnvelopeId = envelopeId
        )
    }

    private suspend fun applyHandshakeReject(json: JSONObject, envelopeId: String) {
        val conversationSeed = json.optString("conversationSeed")
        val responderPublicKey = json.optString("responderPublicKey")
        if (conversationSeed.isBlank() || responderPublicKey.isBlank()) return

        val conversationId = anonymousMessageCodec.buildConversationId(conversationSeed)
        val existing = localConversationStore.getConversation(conversationId) ?: return
        if (existing.sessionStatus != SessionStatus.PENDING) return

        val now = json.optLong("rejectedAt", System.currentTimeMillis())
        localConversationStore.upsertConversation(
            existing.copy(
                peerPublicKey = responderPublicKey,
                sessionStatus = SessionStatus.REJECTED,
                updatedAt = now
            )
        )
        appendSystemMessage(
            targetId = conversationId,
            direction = MessageDirection.INCOMING,
            text = "Собеседник отклонил приглашение",
            sourceEnvelopeId = envelopeId
        )
    }

    private suspend fun handleConversationEnvelope(envelope: com.example.anonymousmeetup.data.model.AnonymousEnvelope): Boolean {
        val candidates = localConversationStore.getConversationsForPool(envelope.poolId)
            .filter { it.sessionStatus != SessionStatus.FAILED && it.sessionStatus != SessionStatus.REJECTED }
        if (candidates.isEmpty()) return false

        for (conversation in candidates) {
            val keyMaterial = conversationSecretStore.getSecret(conversation.sharedSecretRef) ?: continue
            val json = anonymousMessageCodec.tryDecryptSymmetricEnvelope(envelope, keyMaterial) ?: continue
            debugTraceLogger.debug(TAG, "handleConversationEnvelope decrypt success id=${envelope.id} conversation=${conversation.conversationId}")
            if (json.optInt("protocolVersion", -1) != AnonymousMessageCodec.PROTOCOL_VERSION) continue
            if (json.optString("conversationId") != conversation.conversationId) continue

            when (json.optString("type")) {
                "PRIVATE_TEXT" -> {
                    val sentAt = json.optLong("sentAt", envelope.timestamp)
                    localMessageStore.appendMessage(
                        LocalMessageRecord(
                            localMessageId = anonymousMessageCodec.newLocalMessageId(),
                            conversationIdOrGroupLocalId = conversation.conversationId,
                            direction = MessageDirection.INCOMING,
                            type = LocalMessageType.PRIVATE_TEXT,
                            text = json.optString("text"),
                            rawPayloadJson = json.toString(),
                            timestamp = sentAt,
                            sourceEnvelopeId = envelope.id,
                            isRead = false
                        )
                    )
                    localConversationStore.updateStatus(
                        conversationId = conversation.conversationId,
                        status = SessionStatus.ACTIVE,
                        updatedAt = sentAt,
                        acceptedAt = conversation.acceptedAt ?: sentAt,
                        lastMessageAt = sentAt
                    )
                    return true
                }
                "LOCATION" -> {
                    val sentAt = json.optLong("sentAt", envelope.timestamp)
                    val text = "${json.optDouble("latitude")}, ${json.optDouble("longitude") }"
                    localMessageStore.appendMessage(
                        LocalMessageRecord(
                            localMessageId = anonymousMessageCodec.newLocalMessageId(),
                            conversationIdOrGroupLocalId = conversation.conversationId,
                            direction = MessageDirection.INCOMING,
                            type = LocalMessageType.LOCATION,
                            text = text,
                            rawPayloadJson = json.toString(),
                            timestamp = sentAt,
                            sourceEnvelopeId = envelope.id,
                            isRead = false
                        )
                    )
                    localConversationStore.updateStatus(
                        conversationId = conversation.conversationId,
                        status = SessionStatus.ACTIVE,
                        updatedAt = sentAt,
                        acceptedAt = conversation.acceptedAt ?: sentAt,
                        lastMessageAt = sentAt
                    )
                    return true
                }
            }
        }
        return false
    }

    private suspend fun appendSystemMessage(
        targetId: String,
        direction: MessageDirection,
        text: String,
        sourceEnvelopeId: String?
    ) {
        localMessageStore.appendMessage(
            LocalMessageRecord(
                localMessageId = anonymousMessageCodec.newLocalMessageId(),
                conversationIdOrGroupLocalId = targetId,
                direction = direction,
                type = LocalMessageType.SYSTEM,
                text = text,
                rawPayloadJson = null,
                timestamp = System.currentTimeMillis(),
                sourceEnvelopeId = sourceEnvelopeId,
                isRead = direction == MessageDirection.OUTGOING
            )
        )
    }

    private fun LocalConversation.canSendEncryptedMessages(): Boolean {
        return sessionStatus == SessionStatus.ACCEPTED || sessionStatus == SessionStatus.ACTIVE
    }

    private companion object {
        const val TAG = "PrivateChatRepository"
    }
}
