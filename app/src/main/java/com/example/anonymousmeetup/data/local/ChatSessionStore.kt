package com.example.anonymousmeetup.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.anonymousmeetup.data.model.ChatSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chatSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_sessions")

@Singleton
class ChatSessionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.chatSessionDataStore

    private object Keys {
        val SESSIONS_JSON = stringPreferencesKey("sessions_json")
        val SECRETS_JSON = stringPreferencesKey("secrets_json")
    }

    fun observeSessions(): Flow<List<ChatSession>> {
        return dataStore.data.map { prefs ->
            parseSessions(prefs[Keys.SESSIONS_JSON])
        }
    }

    fun observeSessionById(sessionId: String): Flow<ChatSession?> {
        return observeSessions().map { sessions ->
            sessions.firstOrNull { it.sessionId == sessionId }
        }
    }

    fun observeSessionByChatHash(chatHash: String): Flow<ChatSession?> {
        return observeSessions().map { sessions ->
            sessions.firstOrNull { session ->
                session.currentChatHash == chatHash || session.previousChatHashes.contains(chatHash)
            }
        }
    }

    suspend fun getSession(sessionId: String): ChatSession? {
        return observeSessions().first().firstOrNull { it.sessionId == sessionId }
    }

    suspend fun getSessionByChatHash(chatHash: String): ChatSession? {
        return observeSessions().first().firstOrNull { session ->
            session.currentChatHash == chatHash || session.previousChatHashes.contains(chatHash)
        }
    }

    suspend fun upsertSession(session: ChatSession) {
        dataStore.edit { prefs ->
            val current = parseSessions(prefs[Keys.SESSIONS_JSON]).toMutableList()
            val index = current.indexOfFirst { it.sessionId == session.sessionId }
            if (index >= 0) {
                current[index] = session
            } else {
                current.add(session)
            }
            prefs[Keys.SESSIONS_JSON] = serializeSessions(current)
        }
    }

    suspend fun saveSharedSecret(secretRef: String, secret: ByteArray) {
        dataStore.edit { prefs ->
            val map = parseSecrets(prefs[Keys.SECRETS_JSON]).toMutableMap()
            map[secretRef] = Base64.getEncoder().encodeToString(secret)
            prefs[Keys.SECRETS_JSON] = serializeSecrets(map)
        }
    }

    suspend fun getSharedSecret(secretRef: String): ByteArray? {
        val map = parseSecrets(dataStore.data.first()[Keys.SECRETS_JSON])
        val value = map[secretRef] ?: return null
        return try {
            Base64.getDecoder().decode(value)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SESSIONS_JSON)
            prefs.remove(Keys.SECRETS_JSON)
        }
    }

    private fun parseSessions(raw: String?): List<ChatSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        ChatSession(
                            sessionId = obj.optString("sessionId"),
                            peerDisplayName = obj.optString("peerDisplayName"),
                            peerPublicKey = obj.optString("peerPublicKey"),
                            currentChatHash = obj.optString("currentChatHash"),
                            previousChatHashes = parseStringArray(obj.optJSONArray("previousChatHashes")),
                            sharedSecretRef = obj.optString("sharedSecretRef"),
                            createdAt = obj.optLong("createdAt"),
                            rotateAt = if (obj.has("rotateAt") && !obj.isNull("rotateAt")) obj.optLong("rotateAt") else null,
                            isActive = obj.optBoolean("isActive", true)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeSessions(sessions: List<ChatSession>): String {
        val array = JSONArray()
        sessions.forEach { session ->
            val obj = JSONObject()
                .put("sessionId", session.sessionId)
                .put("peerDisplayName", session.peerDisplayName)
                .put("peerPublicKey", session.peerPublicKey)
                .put("currentChatHash", session.currentChatHash)
                .put("previousChatHashes", JSONArray(session.previousChatHashes))
                .put("sharedSecretRef", session.sharedSecretRef)
                .put("createdAt", session.createdAt)
                .put("rotateAt", session.rotateAt)
                .put("isActive", session.isActive)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseSecrets(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { key -> json.optString(key) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun serializeSecrets(secrets: Map<String, String>): String {
        val json = JSONObject()
        secrets.forEach { (key, value) ->
            json.put(key, value)
        }
        return json.toString()
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optString(i)
                if (item.isNotBlank()) add(item)
            }
        }
    }
}
