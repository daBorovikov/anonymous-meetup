package com.example.anonymousmeetup.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.conversationSecretDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "conversation_secret_store"
)

@Singleton
class ConversationSecretStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.conversationSecretDataStore

    private object Keys {
        val SECRETS_JSON = stringPreferencesKey("conversation_secrets_json")
    }

    suspend fun saveSecret(secretRef: String, secret: ByteArray) {
        dataStore.edit { prefs ->
            val json = prefs[Keys.SECRETS_JSON]?.let(::JSONObject) ?: JSONObject()
            json.put(secretRef, Base64.getEncoder().encodeToString(secret))
            prefs[Keys.SECRETS_JSON] = json.toString()
        }
    }

    suspend fun getSecret(secretRef: String): ByteArray? {
        val raw = dataStore.data.map { it[Keys.SECRETS_JSON] }.first() ?: return null
        return try {
            val value = JSONObject(raw).optString(secretRef)
            if (value.isBlank()) null else Base64.getDecoder().decode(value)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun removeSecret(secretRef: String) {
        dataStore.edit { prefs ->
            val raw = prefs[Keys.SECRETS_JSON] ?: return@edit
            val json = JSONObject(raw)
            json.remove(secretRef)
            prefs[Keys.SECRETS_JSON] = json.toString()
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SECRETS_JSON)
        }
    }
}
