package com.example.anonymousmeetup.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.anonymousmeetup.data.model.LocalMessageRecord
import com.example.anonymousmeetup.data.model.LocalMessageType
import com.example.anonymousmeetup.data.model.MessageDirection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.localMessageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "local_messages_store"
)

@Singleton
class LocalMessageStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.localMessageDataStore

    private object Keys {
        val MESSAGES_JSON = stringPreferencesKey("messages_json")
    }

    fun observeMessages(targetId: String): Flow<List<LocalMessageRecord>> {
        return dataStore.data.map { prefs ->
            parseMessages(prefs[Keys.MESSAGES_JSON])
                .filter { it.conversationIdOrGroupLocalId == targetId }
                .sortedBy { it.timestamp }
        }
    }

    suspend fun getMessages(targetId: String): List<LocalMessageRecord> {
        return observeMessages(targetId).first()
    }

    suspend fun appendMessage(record: LocalMessageRecord) {
        dataStore.edit { prefs ->
            val items = parseMessages(prefs[Keys.MESSAGES_JSON]).toMutableList()
            val duplicate = items.any {
                it.localMessageId == record.localMessageId ||
                    (
                        !record.sourceEnvelopeId.isNullOrBlank() &&
                            record.sourceEnvelopeId == it.sourceEnvelopeId &&
                            record.conversationIdOrGroupLocalId == it.conversationIdOrGroupLocalId
                        )
            }
            if (!duplicate) {
                items.add(record)
            }
            prefs[Keys.MESSAGES_JSON] = serializeMessages(items.takeLast(MAX_MESSAGES))
        }
    }

    suspend fun clearHistory(targetId: String) {
        dataStore.edit { prefs ->
            val remaining = parseMessages(prefs[Keys.MESSAGES_JSON])
                .filterNot { it.conversationIdOrGroupLocalId == targetId }
            prefs[Keys.MESSAGES_JSON] = serializeMessages(remaining)
        }
    }

    suspend fun clearAllHistory() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.MESSAGES_JSON)
        }
    }

    private fun parseMessages(raw: String?): List<LocalMessageRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        LocalMessageRecord(
                            localMessageId = item.optString("localMessageId"),
                            conversationIdOrGroupLocalId = item.optString("targetId"),
                            direction = runCatching {
                                MessageDirection.valueOf(item.optString("direction", MessageDirection.INCOMING.name))
                            }.getOrDefault(MessageDirection.INCOMING),
                            type = runCatching {
                                LocalMessageType.valueOf(item.optString("type", LocalMessageType.SYSTEM.name))
                            }.getOrDefault(LocalMessageType.SYSTEM),
                            text = item.optString("text").ifBlank { null },
                            rawPayloadJson = item.optString("rawPayloadJson").ifBlank { null },
                            timestamp = item.optLong("timestamp"),
                            sourceEnvelopeId = item.optString("sourceEnvelopeId").ifBlank { null },
                            isRead = item.optBoolean("isRead", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeMessages(items: List<LocalMessageRecord>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("localMessageId", item.localMessageId)
                    .put("targetId", item.conversationIdOrGroupLocalId)
                    .put("direction", item.direction.name)
                    .put("type", item.type.name)
                    .put("text", item.text)
                    .put("rawPayloadJson", item.rawPayloadJson)
                    .put("timestamp", item.timestamp)
                    .put("sourceEnvelopeId", item.sourceEnvelopeId)
                    .put("isRead", item.isRead)
            )
        }
        return array.toString()
    }

    companion object {
        private const val MAX_MESSAGES = 5000
    }
}


