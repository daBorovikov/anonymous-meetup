package com.example.anonymousmeetup.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.anonymousmeetup.data.model.EncounterLocal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.encountersDataStore: DataStore<Preferences> by preferencesDataStore(name = "encounter_store")

@Singleton
class EncounterStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.encountersDataStore

    private object Keys {
        val ENCOUNTERS_JSON = stringPreferencesKey("encounters_json")
    }

    fun observeEncounters(): Flow<List<EncounterLocal>> {
        return dataStore.data.map { prefs ->
            parseEncounters(prefs[Keys.ENCOUNTERS_JSON]).sortedByDescending { it.happenedAt }
        }
    }

    suspend fun addEncounter(encounter: EncounterLocal) {
        dataStore.edit { prefs ->
            val current = parseEncounters(prefs[Keys.ENCOUNTERS_JSON]).toMutableList()
            val index = current.indexOfFirst { it.encounterId == encounter.encounterId }
            if (index >= 0) {
                current[index] = encounter
            } else {
                current.add(encounter)
            }
            prefs[Keys.ENCOUNTERS_JSON] = serializeEncounters(current.takeLast(500))
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ENCOUNTERS_JSON)
        }
    }

    private fun parseEncounters(raw: String?): List<EncounterLocal> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        EncounterLocal(
                            encounterId = item.optString("encounterId"),
                            alias = item.optString("alias").ifBlank { null },
                            publicKeyFingerprint = item.optString("publicKeyFingerprint").ifBlank { null },
                            groupHash = item.optString("groupHash").ifBlank { null },
                            groupName = item.optString("groupName").ifBlank { null },
                            distanceMeters = item.optDouble("distanceMeters", 0.0),
                            happenedAt = item.optLong("happenedAt")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeEncounters(items: List<EncounterLocal>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("encounterId", item.encounterId)
                    .put("alias", item.alias)
                    .put("publicKeyFingerprint", item.publicKeyFingerprint)
                    .put("groupHash", item.groupHash)
                    .put("groupName", item.groupName)
                    .put("distanceMeters", item.distanceMeters)
                    .put("happenedAt", item.happenedAt)
            )
        }
        return array.toString()
    }
}
