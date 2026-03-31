package com.example.anonymousmeetup.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.anonymousmeetup.data.model.GroupKeyRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.groupKeyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "group_keys_store"
)

@Singleton
class GroupKeyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.groupKeyDataStore

    private object Keys {
        val GROUP_KEYS_JSON = stringPreferencesKey("group_keys_json")
    }

    fun observeGroupKeys(): Flow<List<GroupKeyRecord>> {
        return dataStore.data.map { prefs ->
            parseKeys(prefs[Keys.GROUP_KEYS_JSON]).sortedBy { it.localGroupName.lowercase() }
        }
    }

    fun observeGroupKey(groupLocalId: String): Flow<GroupKeyRecord?> {
        return observeGroupKeys().map { keys ->
            keys.firstOrNull { it.groupLocalId == groupLocalId }
        }
    }

    suspend fun getGroupKey(groupLocalId: String): GroupKeyRecord? {
        return observeGroupKeys().first().firstOrNull { it.groupLocalId == groupLocalId }
    }

    suspend fun getKeysForPool(poolId: String): List<GroupKeyRecord> {
        return observeGroupKeys().first().filter { it.poolId == poolId }
    }

    suspend fun upsertGroupKey(record: GroupKeyRecord) {
        dataStore.edit { prefs ->
            val keys = parseKeys(prefs[Keys.GROUP_KEYS_JSON]).toMutableList()
            val index = keys.indexOfFirst { it.groupLocalId == record.groupLocalId }
            if (index >= 0) {
                keys[index] = record
            } else {
                keys.add(record)
            }
            prefs[Keys.GROUP_KEYS_JSON] = serializeKeys(keys.takeLast(MAX_GROUP_KEYS))
        }
    }

    suspend fun removeGroupKey(groupLocalId: String) {
        dataStore.edit { prefs ->
            val keys = parseKeys(prefs[Keys.GROUP_KEYS_JSON]).filterNot { it.groupLocalId == groupLocalId }
            prefs[Keys.GROUP_KEYS_JSON] = serializeKeys(keys)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.GROUP_KEYS_JSON)
        }
    }

    private fun parseKeys(raw: String?): List<GroupKeyRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        GroupKeyRecord(
                            groupLocalId = item.optString("groupLocalId"),
                            localGroupName = item.optString("localGroupName"),
                            poolId = item.optString("poolId"),
                            groupKey = item.optString("groupKey"),
                            createdAt = item.optLong("createdAt"),
                            updatedAt = item.optLong("updatedAt", item.optLong("createdAt"))
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeKeys(items: List<GroupKeyRecord>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("groupLocalId", item.groupLocalId)
                    .put("localGroupName", item.localGroupName)
                    .put("poolId", item.poolId)
                    .put("groupKey", item.groupKey)
                    .put("createdAt", item.createdAt)
                    .put("updatedAt", item.updatedAt)
            )
        }
        return array.toString()
    }

    companion object {
        private const val MAX_GROUP_KEYS = 500
    }
}
