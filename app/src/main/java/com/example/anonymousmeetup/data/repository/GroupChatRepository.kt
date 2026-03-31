package com.example.anonymousmeetup.data.repository

import com.example.anonymousmeetup.data.debug.DebugTraceLogger
import com.example.anonymousmeetup.data.local.GroupKeyStore
import com.example.anonymousmeetup.data.local.LocalMessageStore
import com.example.anonymousmeetup.data.local.ProcessedEnvelopeStore
import com.example.anonymousmeetup.data.model.LocalMessageRecord
import com.example.anonymousmeetup.data.model.LocalMessageType
import com.example.anonymousmeetup.data.model.MessageDirection
import com.example.anonymousmeetup.data.model.ProcessedEnvelopeOutcome
import com.example.anonymousmeetup.data.model.ProcessedEnvelopeRecord
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.remote.FirebaseService
import com.example.anonymousmeetup.data.security.AnonymousMessageCodec
import com.example.anonymousmeetup.data.security.AnonymousPools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupChatRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val userPreferences: UserPreferences,
    private val userRepository: UserRepository,
    private val groupKeyStore: GroupKeyStore,
    private val localMessageStore: LocalMessageStore,
    private val processedEnvelopeStore: ProcessedEnvelopeStore,
    private val anonymousMessageCodec: AnonymousMessageCodec,
    private val debugTraceLogger: DebugTraceLogger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastSeenByPool = mutableMapOf<String, Long>()
    private var listenersStarted = false

    fun observeGroupMessages(groupLocalId: String): Flow<List<LocalMessageRecord>> {
        ensureListenersStarted()
        return localMessageStore.observeMessages(groupLocalId)
    }

    suspend fun sendGroupMessage(groupLocalId: String, text: String, senderAlias: String? = null) {
        ensureListenersStarted()
        val groupKey = groupKeyStore.getGroupKey(groupLocalId)
            ?: throw IllegalStateException("Группа не подключена локально")
        val identity = userRepository.ensureIdentityKeys()
        val alias = senderAlias ?: userPreferences.getNickname().orEmpty().ifBlank { "Аноним" }
        val sentAt = System.currentTimeMillis()
        val payload = JSONObject()
            .put("type", "GROUP_TEXT")
            .put("protocolVersion", AnonymousMessageCodec.PROTOCOL_VERSION)
            .put("groupLocalIdOrRoutingHint", groupLocalId)
            .put("text", text)
            .put("sentAt", sentAt)
            .put("senderAlias", alias)
            .put("senderPublicKey", identity.publicKey)

        debugTraceLogger.debug(TAG, "sendGroupMessage groupId=$groupLocalId poolId=${groupKey.poolId} textLength=${text.length}")
        val envelope = anonymousMessageCodec.createSymmetricEnvelope(
            poolId = groupKey.poolId,
            keyMaterial = java.util.Base64.getDecoder().decode(groupKey.groupKey),
            payloadJson = payload
        )
        firebaseService.sendAnonymousEnvelope(envelope)
        localMessageStore.appendMessage(
            LocalMessageRecord(
                localMessageId = anonymousMessageCodec.newLocalMessageId(),
                conversationIdOrGroupLocalId = groupLocalId,
                direction = MessageDirection.OUTGOING,
                type = LocalMessageType.GROUP_TEXT,
                text = text,
                rawPayloadJson = payload.toString(),
                timestamp = sentAt,
                sourceEnvelopeId = null,
                isRead = true
            )
        )
    }

    private fun ensureListenersStarted() {
        if (listenersStarted) return
        listenersStarted = true
        AnonymousPools.GROUP_POOLS.forEach { poolId ->
            scope.launch {
                val since = processedEnvelopeStore.getLastSeen(poolId)
                lastSeenByPool[poolId] = since
                firebaseService.listenAnonymousPool(poolId, since).collect { envelopes ->
                    for (envelope in envelopes.sortedBy { it.timestamp }) {
                        if (processedEnvelopeStore.isProcessed(envelope.id)) {
                            processedEnvelopeStore.updateLastSeen(poolId, envelope.timestamp)
                            continue
                        }
                        val outcome = routeEnvelope(envelope)
                        processedEnvelopeStore.markProcessed(
                            ProcessedEnvelopeRecord(
                                envelopeId = envelope.id,
                                poolId = envelope.poolId,
                                processedAt = System.currentTimeMillis(),
                                outcome = outcome
                            )
                        )
                        processedEnvelopeStore.updateLastSeen(poolId, envelope.timestamp)
                        lastSeenByPool[poolId] = maxOf(lastSeenByPool[poolId] ?: 0L, envelope.timestamp)
                    }
                }
            }
        }
    }

    private suspend fun routeEnvelope(envelope: com.example.anonymousmeetup.data.model.AnonymousEnvelope): ProcessedEnvelopeOutcome {
        val keys = groupKeyStore.getKeysForPool(envelope.poolId)
        if (keys.isEmpty()) {
            debugTraceLogger.debug(TAG, "routeEnvelope ignored id=${envelope.id} poolId=${envelope.poolId} reason=no_keys")
            return ProcessedEnvelopeOutcome.IGNORED
        }

        val myIdentityKey = runCatching { userRepository.ensureIdentityKeys().publicKey }.getOrNull()
        keys.forEach { keyRecord ->
            val json = anonymousMessageCodec.tryDecryptSymmetricEnvelope(
                envelope = envelope,
                keyMaterial = java.util.Base64.getDecoder().decode(keyRecord.groupKey)
            ) ?: return@forEach

            debugTraceLogger.debug(TAG, "routeEnvelope decrypt success id=${envelope.id} groupId=${keyRecord.groupLocalId}")
            if (json.optInt("protocolVersion", -1) != AnonymousMessageCodec.PROTOCOL_VERSION) {
                return@forEach
            }
            if (json.optString("type") != "GROUP_TEXT") {
                return@forEach
            }
            if (json.optString("groupLocalIdOrRoutingHint") != keyRecord.groupLocalId) {
                return@forEach
            }
            if (!myIdentityKey.isNullOrBlank() && json.optString("senderPublicKey") == myIdentityKey) {
                debugTraceLogger.debug(TAG, "routeEnvelope self message id=${envelope.id} ignored after decrypt")
                return ProcessedEnvelopeOutcome.ROUTED
            }

            localMessageStore.appendMessage(
                LocalMessageRecord(
                    localMessageId = anonymousMessageCodec.newLocalMessageId(),
                    conversationIdOrGroupLocalId = keyRecord.groupLocalId,
                    direction = MessageDirection.INCOMING,
                    type = LocalMessageType.GROUP_TEXT,
                    text = json.optString("text"),
                    rawPayloadJson = json.toString(),
                    timestamp = json.optLong("sentAt", envelope.timestamp),
                    sourceEnvelopeId = envelope.id,
                    isRead = false
                )
            )
            debugTraceLogger.debug(TAG, "routeEnvelope routed id=${envelope.id} groupId=${keyRecord.groupLocalId}")
            return ProcessedEnvelopeOutcome.ROUTED
        }
        debugTraceLogger.debug(TAG, "routeEnvelope ignored id=${envelope.id} poolId=${envelope.poolId} reason=decrypt_failed")
        return ProcessedEnvelopeOutcome.IGNORED
    }

    private companion object {
        const val TAG = "GroupChatRepository"
    }
}
