package com.example.anonymousmeetup.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.anonymousmeetup.data.model.Group
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.joinedGroupsDataStore: DataStore<Preferences> by preferencesDataStore(name = "joined_groups_store")

@Singleton
class JoinedGroupsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.joinedGroupsDataStore

    private object Keys {
        val JOINED_GROUPS_JSON = stringPreferencesKey("joined_groups_json")
    }

    fun observeJoinedGroups(): Flow<List<Group>> {
        return dataStore.data.map { prefs ->
            parseGroups(prefs[Keys.JOINED_GROUPS_JSON])
        }
    }

    suspend fun getJoinedGroups(): List<Group> = observeJoinedGroups().first()

    suspend fun isJoined(groupId: String): Boolean {
        return observeJoinedGroups().first().any { it.id == groupId }
    }

    suspend fun join(group: Group) {
        dataStore.edit { prefs ->
            val groups = parseGroups(prefs[Keys.JOINED_GROUPS_JSON]).toMutableList()
            val index = groups.indexOfFirst { it.id == group.id }
            if (index >= 0) {
                groups[index] = group
            } else {
                groups.add(group)
            }
            prefs[Keys.JOINED_GROUPS_JSON] = serializeGroups(groups)
        }
    }

    suspend fun leave(groupId: String) {
        dataStore.edit { prefs ->
            val groups = parseGroups(prefs[Keys.JOINED_GROUPS_JSON]).filterNot { it.id == groupId }
            prefs[Keys.JOINED_GROUPS_JSON] = serializeGroups(groups)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.JOINED_GROUPS_JSON)
        }
    }

    private fun parseGroups(raw: String?): List<Group> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        Group(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            description = item.optString("description"),
                            category = item.optString("category"),
                            isPrivate = item.optBoolean("isPrivate", false),
                            groupHash = item.optString("groupHash"),
                            poolId = item.optString("poolId"),
                            joinToken = item.optString("joinToken").ifBlank { null },
                            createdAt = item.optLong("createdAt")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeGroups(groups: List<Group>): String {
        val array = JSONArray()
        groups.forEach { group ->
            array.put(
                JSONObject()
                    .put("id", group.id)
                    .put("name", group.name)
                    .put("description", group.description)
                    .put("category", group.category)
                    .put("isPrivate", group.isPrivate)
                    .put("groupHash", group.groupHash)
                    .put("poolId", group.poolId)
                    .put("joinToken", group.joinToken)
                    .put("createdAt", group.createdAt)
            )
        }
        return array.toString()
    }
}
