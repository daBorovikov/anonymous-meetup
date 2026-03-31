package com.example.anonymousmeetup.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.anonymousmeetup.data.model.ProcessedEnvelopeOutcome
import com.example.anonymousmeetup.data.model.ProcessedEnvelopeRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.processedEnvelopeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "processed_envelopes_store"
)

@Singleton
class ProcessedEnvelopeStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.processedEnvelopeDataStore

    private object Keys {
        val ENVELOPES_JSON = stringPreferencesKey("processed_envelopes_json")
        val LAST_SEEN_JSON = stringPreferencesKey("last_seen_json")
    }

    suspend fun isProcessed(envelopeId: String): Boolean {
        return getProcessed().any { it.envelopeId == envelopeId }
    }

    suspend fun markProcessed(record: ProcessedEnvelopeRecord) {
        dataStore.edit { prefs ->
            val processed = parseProcessed(prefs[Keys.ENVELOPES_JSON]).toMutableList()
            if (processed.none { it.envelopeId == record.envelopeId }) {
                processed.add(record)
            }
            prefs[Keys.ENVELOPES_JSON] = serializeProcessed(processed.takeLast(MAX_PROCESSED))
        }
    }

    suspend fun getLastSeen(poolId: String): Long {
        val json = dataStore.data.map { it[Keys.LAST_SEEN_JSON] }.first()
        return try {
            val obj = if (json.isNullOrBlank()) JSONObject() else JSONObject(json)
            obj.optLong(poolId, 0L)
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun updateLastSeen(poolId: String, timestamp: Long) {
        dataStore.edit { prefs ->
            val raw = prefs[Keys.LAST_SEEN_JSON]
            val obj = if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
            val current = obj.optLong(poolId, 0L)
            if (timestamp > current) {
                obj.put(poolId, timestamp)
            }
            prefs[Keys.LAST_SEEN_JSON] = obj.toString()
        }
    }

    suspend fun clearProcessed() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ENVELOPES_JSON)
            prefs.remove(Keys.LAST_SEEN_JSON)
        }
    }

    private suspend fun getProcessed(): List<ProcessedEnvelopeRecord> {
        val raw = dataStore.data.map { it[Keys.ENVELOPES_JSON] }.first()
        return parseProcessed(raw)
    }

    private fun parseProcessed(raw: String?): List<ProcessedEnvelopeRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        ProcessedEnvelopeRecord(
                            envelopeId = item.optString("envelopeId"),
                            poolId = item.optString("poolId"),
                            processedAt = item.optLong("processedAt"),
                            outcome = runCatching {
                                ProcessedEnvelopeOutcome.valueOf(
                                    item.optString("outcome", ProcessedEnvelopeOutcome.IGNORED.name)
                                )
                            }.getOrDefault(ProcessedEnvelopeOutcome.IGNORED)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeProcessed(items: List<ProcessedEnvelopeRecord>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("envelopeId", item.envelopeId)
                    .put("poolId", item.poolId)
                    .put("processedAt", item.processedAt)
                    .put("outcome", item.outcome.name)
            )
        }
        return array.toString()
    }

    companion object {
        private const val MAX_PROCESSED = 10000
    }
}


