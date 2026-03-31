package com.example.anonymousmeetup.data.remote

import com.example.anonymousmeetup.data.debug.DebugTraceLogger
import com.example.anonymousmeetup.data.model.AnonymousEnvelope
import com.example.anonymousmeetup.data.model.Group
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val debugTraceLogger: DebugTraceLogger
) {
    private val groupsCollection = firestore.collection("groups")
    private val anonymousEnvelopesCollection = firestore.collection("anonymous_envelopes")

    suspend fun searchGroups(query: String): List<Group> {
        return try {
            val normalized = query.trim().lowercase()
            val snapshots = groupsCollection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(250)
                .get()
                .await()
                .documents

            val all = snapshots.mapNotNull { doc ->
                val raw = doc.data
                val legacyFields = GroupDocumentContract.findLegacyFields(raw)
                if (legacyFields.isNotEmpty()) {
                    debugTraceLogger.debug(
                        TAG,
                        "searchGroups legacy fields for ${doc.id}: ${legacyFields.sorted().joinToString()}"
                    )
                }
                GroupDocumentContract.fromMap(doc.id, raw)
            }

            val filtered = if (normalized.isBlank()) {
                all.take(50)
            } else {
                all.filter { group ->
                    group.name.lowercase().contains(normalized) ||
                        group.category.lowercase().contains(normalized) ||
                        group.description.lowercase().contains(normalized)
                }
            }
            debugTraceLogger.debug(TAG, "searchGroups query='$query' results=${filtered.size}")
            filtered
        } catch (e: Exception) {
            debugTraceLogger.error(TAG, "Error searching groups for query='$query'", e)
            emptyList()
        }
    }

    suspend fun createGroup(group: Group): String {
        val payload = GroupDocumentContract.toFirestorePayload(group)
        val contractErrors = GroupDocumentContract.validatePayload(payload)
        debugTraceLogger.debug(TAG, "createGroup payload=${JSONObject(payload).toString()}")
        if (contractErrors.isNotEmpty()) {
            val message = "Group payload does not match firestore rules: ${contractErrors.joinToString()}"
            debugTraceLogger.error(TAG, message)
            throw IllegalArgumentException(message)
        }
        return try {
            groupsCollection.document(group.id).set(payload).await()
            debugTraceLogger.debug(TAG, "createGroup success id=${group.id}")
            group.id
        } catch (e: Exception) {
            debugTraceLogger.error(TAG, "createGroup failed id=${group.id}", e)
            throw e
        }
    }

    suspend fun updateGroup(group: Group) {
        val payload = GroupDocumentContract.toFirestorePayload(group)
        val contractErrors = GroupDocumentContract.validatePayload(payload)
        debugTraceLogger.debug(TAG, "updateGroup payload=${JSONObject(payload).toString()}")
        if (contractErrors.isNotEmpty()) {
            val message = "Group payload does not match firestore rules: ${contractErrors.joinToString()}"
            debugTraceLogger.error(TAG, message)
            throw IllegalArgumentException(message)
        }
        groupsCollection.document(group.id).set(payload).await()
    }

    suspend fun deleteGroup(groupId: String) {
        groupsCollection.document(groupId).delete().await()
    }

    suspend fun getGroup(groupId: String): Group? {
        return try {
            val snapshot = groupsCollection.document(groupId).get().await()
            if (!snapshot.exists()) return null
            val raw = snapshot.data
            val legacyFields = GroupDocumentContract.findLegacyFields(raw)
            if (legacyFields.isNotEmpty()) {
                debugTraceLogger.debug(
                    TAG,
                    "getGroup legacy fields for $groupId: ${legacyFields.sorted().joinToString()}"
                )
            }
            GroupDocumentContract.fromMap(snapshot.id, raw)
        } catch (e: Exception) {
            debugTraceLogger.error(TAG, "Error getting group id=$groupId", e)
            null
        }
    }

    fun listenAnonymousPool(poolId: String, sinceTimestamp: Long): Flow<List<AnonymousEnvelope>> = callbackFlow {
        val floor = if (sinceTimestamp > 0L) (sinceTimestamp - 60_000L).coerceAtLeast(0L) else 0L
        debugTraceLogger.debug(TAG, "listenAnonymousPool poolId=$poolId since=$sinceTimestamp floor=$floor")
        val query = anonymousEnvelopesCollection
            .whereEqualTo("poolId", poolId)
            .whereGreaterThanOrEqualTo("timestamp", floor)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(250)

        val registration: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                debugTraceLogger.error(TAG, "Pool listener error for $poolId", error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            val items = snapshot?.documents.orEmpty().mapNotNull { doc ->
                doc.toObject(AnonymousEnvelope::class.java)?.copy(id = doc.id)
            }
            debugTraceLogger.debug(TAG, "listenAnonymousPool delivered poolId=$poolId count=${items.size}")
            trySend(items)
        }

        awaitClose { registration.remove() }
    }

    suspend fun sendAnonymousEnvelope(envelope: AnonymousEnvelope): String {
        debugTraceLogger.debug(
            TAG,
            "sendAnonymousEnvelope poolId=${envelope.poolId} version=${envelope.version} ciphertextLength=${envelope.ciphertext.length}"
        )
        val docRef = anonymousEnvelopesCollection.add(envelope).await()
        debugTraceLogger.debug(TAG, "sendAnonymousEnvelope success id=${docRef.id} poolId=${envelope.poolId}")
        return docRef.id
    }

    companion object {
        private const val TAG = "FirebaseService"
    }
}
