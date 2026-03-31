package com.example.anonymousmeetup.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.anonymousmeetup.data.model.LocalConversation
import com.example.anonymousmeetup.data.model.SessionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.localConversationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "local_conversations_store"
)

@Singleton
class LocalConversationStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.localConversationDataStore

    private object Keys {
        val CONVERSATIONS_JSON = stringPreferencesKey("conversations_json")
    }

    fun observeConversations(): Flow<List<LocalConversation>> {
        return dataStore.data.map { prefs ->
            parseConversations(prefs[Keys.CONVERSATIONS_JSON]).sortedByDescending { it.updatedAt }
        }
    }

    fun observeConversation(conversationId: String): Flow<LocalConversation?> {
        return observeConversations().map { items ->
            items.firstOrNull { it.conversationId == conversationId }
        }
    }

    suspend fun getConversation(conversationId: String): LocalConversation? {
        return observeConversations().first().firstOrNull { it.conversationId == conversationId }
    }

    suspend fun getConversationBySeed(conversationSeed: String): LocalConversation? {
        return observeConversations().first().firstOrNull { it.conversationSeed == conversationSeed }
    }

    suspend fun getConversationByPeerKey(peerPublicKey: String): LocalConversation? {
        return observeConversations().first().firstOrNull { it.peerPublicKey == peerPublicKey }
    }

    suspend fun getConversationsForPool(poolId: String): List<LocalConversation> {
        return observeConversations().first().filter { it.poolId == poolId }
    }

    suspend fun upsertConversation(conversation: LocalConversation) {
        dataStore.edit { prefs ->
            val items = parseConversations(prefs[Keys.CONVERSATIONS_JSON]).toMutableList()
            val index = items.indexOfFirst { it.conversationId == conversation.conversationId }
            if (index >= 0) {
                items[index] = conversation
            } else {
                items.add(conversation)
            }
            prefs[Keys.CONVERSATIONS_JSON] = serializeConversations(items.takeLast(MAX_CONVERSATIONS))
        }
    }

    suspend fun updateStatus(
        conversationId: String,
        status: SessionStatus,
        updatedAt: Long,
        acceptedAt: Long? = null,
        lastMessageAt: Long? = null
    ) {
        val conversation = getConversation(conversationId) ?: return
        upsertConversation(
            conversation.copy(
                sessionStatus = status,
                updatedAt = updatedAt,
                acceptedAt = acceptedAt ?: conversation.acceptedAt,
                lastMessageAt = lastMessageAt ?: conversation.lastMessageAt
            )
        )
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.CONVERSATIONS_JSON)
        }
    }

    private fun parseConversations(raw: String?): List<LocalConversation> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        LocalConversation(
                            conversationId = item.optString("conversationId"),
                            peerPublicKey = item.optString("peerPublicKey"),
                            localAlias = item.optString("localAlias").ifBlank { null },
                            sessionStatus = runCatching {
                                SessionStatus.valueOf(item.optString("sessionStatus", SessionStatus.PENDING.name))
                            }.getOrDefault(SessionStatus.PENDING),
                            sharedSecretRef = item.optString("sharedSecretRef"),
                            poolId = item.optString("poolId"),
                            conversationSeed = item.optString("conversationSeed"),
                            createdAt = item.optLong("createdAt"),
                            updatedAt = item.optLong("updatedAt"),
                            acceptedAt = item.optLong("acceptedAt").takeIf { item.has("acceptedAt") && !item.isNull("acceptedAt") },
                            lastMessageAt = item.optLong("lastMessageAt").takeIf { item.has("lastMessageAt") && !item.isNull("lastMessageAt") },
                            isInitiator = item.optBoolean("isInitiator", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeConversations(items: List<LocalConversation>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("conversationId", item.conversationId)
                    .put("peerPublicKey", item.peerPublicKey)
                    .put("localAlias", item.localAlias)
                    .put("sessionStatus", item.sessionStatus.name)
                    .put("sharedSecretRef", item.sharedSecretRef)
                    .put("poolId", item.poolId)
                    .put("conversationSeed", item.conversationSeed)
                    .put("createdAt", item.createdAt)
                    .put("updatedAt", item.updatedAt)
                    .put("acceptedAt", item.acceptedAt)
                    .put("lastMessageAt", item.lastMessageAt)
                    .put("isInitiator", item.isInitiator)
            )
        }
        return array.toString()
    }

    companion object {
        private const val MAX_CONVERSATIONS = 500
    }
}


