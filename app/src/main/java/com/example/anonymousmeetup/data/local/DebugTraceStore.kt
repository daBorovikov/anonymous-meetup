package com.example.anonymousmeetup.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.anonymousmeetup.data.model.DebugTraceEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.debugTraceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "debug_trace_store"
)

@Singleton
class DebugTraceStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.debugTraceDataStore

    private object Keys {
        val TRACE_JSON = stringPreferencesKey("trace_json")
    }

    fun observeEntries(): Flow<List<DebugTraceEntry>> {
        return dataStore.data.map { prefs ->
            parseEntries(prefs[Keys.TRACE_JSON]).sortedByDescending { it.timestamp }
        }
    }

    suspend fun append(tag: String, message: String, level: String, timestamp: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            val items = parseEntries(prefs[Keys.TRACE_JSON]).toMutableList()
            items.add(
                DebugTraceEntry(
                    id = UUID.randomUUID().toString(),
                    tag = tag,
                    message = message,
                    level = level,
                    timestamp = timestamp
                )
            )
            prefs[Keys.TRACE_JSON] = serializeEntries(items.takeLast(MAX_ENTRIES))
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.TRACE_JSON)
        }
    }

    private fun parseEntries(raw: String?): List<DebugTraceEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        DebugTraceEntry(
                            id = item.optString("id"),
                            tag = item.optString("tag"),
                            message = item.optString("message"),
                            level = item.optString("level", "DEBUG"),
                            timestamp = item.optLong("timestamp")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeEntries(items: List<DebugTraceEntry>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("tag", item.tag)
                    .put("message", item.message)
                    .put("level", item.level)
                    .put("timestamp", item.timestamp)
            )
        }
        return array.toString()
    }

    private companion object {
        const val MAX_ENTRIES = 400
    }
}
