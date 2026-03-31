package com.example.anonymousmeetup.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private object PreferencesKeys {
        val NICKNAME = stringPreferencesKey("nickname")
        val IS_AUTHORIZED = booleanPreferencesKey("is_authorized")
        val PUBLIC_KEY = stringPreferencesKey("public_key")
        val PRIVATE_KEY = stringPreferencesKey("private_key")
        val KEY_DATE = stringPreferencesKey("key_date")
        val IDENTITY_PUBLIC_KEY = stringPreferencesKey("identity_public_key")
        val IDENTITY_PRIVATE_KEY = stringPreferencesKey("identity_private_key")
        val KEYS_JSON = stringPreferencesKey("keys_json")
        val ENABLED_GROUPS = stringSetPreferencesKey("enabled_groups")
        val LOCATION_TRACKING_ENABLED = booleanPreferencesKey("location_tracking_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SECURE_BACKUP_ENABLED = booleanPreferencesKey("secure_backup_enabled")
    }

    val nickname: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NICKNAME]
    }

    val isAuthorized: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_AUTHORIZED] ?: false
    }

    val publicKey: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PUBLIC_KEY]
    }

    val privateKey: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PRIVATE_KEY]
    }

    val keyDate: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.KEY_DATE]
    }

    val identityPublicKey: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IDENTITY_PUBLIC_KEY]
    }

    val identityPrivateKey: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IDENTITY_PRIVATE_KEY]
    }

    val enabledGroups: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ENABLED_GROUPS] ?: emptySet()
    }

    val isLocationTrackingEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LOCATION_TRACKING_ENABLED] ?: false
    }

    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true
    }

    val secureBackupEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SECURE_BACKUP_ENABLED] ?: false
    }

    suspend fun getNickname(): String? {
        val nickname = nickname.first()
        Log.d("UserPreferences", "Получен никнейм: $nickname")
        return nickname
    }

    suspend fun isUserAuthorized(): Boolean {
        val isAuthorized = isAuthorized.first()
        Log.d("UserPreferences", "Проверка авторизации: $isAuthorized")
        return isAuthorized
    }

    suspend fun saveNickname(nickname: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NICKNAME] = nickname
            preferences[PreferencesKeys.IS_AUTHORIZED] = true
        }
    }

    suspend fun savePublicKey(publicKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PUBLIC_KEY] = publicKey
        }
    }

    suspend fun savePrivateKey(privateKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRIVATE_KEY] = privateKey
        }
    }

    suspend fun saveIdentityKeyPair(publicKey: String, privateKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IDENTITY_PUBLIC_KEY] = publicKey
            preferences[PreferencesKeys.IDENTITY_PRIVATE_KEY] = privateKey
        }
    }

    suspend fun getPublicKey(): String? = publicKey.first()

    suspend fun getPrivateKey(): String? = privateKey.first()

    suspend fun getIdentityPublicKey(): String? = identityPublicKey.first()

    suspend fun getIdentityPrivateKey(): String? = identityPrivateKey.first()

    suspend fun getCurrentKeyDate(): String? = keyDate.first()

    suspend fun saveKeyPairForDate(date: String, publicKey: String, privateKey: String) {
        val updatedJson = updateKeysJson(date, publicKey, privateKey)
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PUBLIC_KEY] = publicKey
            preferences[PreferencesKeys.PRIVATE_KEY] = privateKey
            preferences[PreferencesKeys.KEY_DATE] = date
            preferences[PreferencesKeys.KEYS_JSON] = updatedJson.toString()
        }
    }

    suspend fun getKeyPairForDate(date: String): Pair<String, String>? {
        val keysJson = dataStore.data.map { it[PreferencesKeys.KEYS_JSON] }.first() ?: return null
        return try {
            val json = JSONObject(keysJson)
            val entry = json.optJSONObject(date) ?: return null
            val publicKey = entry.optString("publicKey", "")
            val privateKey = entry.optString("privateKey", "")
            if (publicKey.isBlank() || privateKey.isBlank()) null else Pair(publicKey, privateKey)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getAllKeyDates(): List<String> {
        val keysJson = dataStore.data.map { it[PreferencesKeys.KEYS_JSON] }.first() ?: return emptyList()
        return try {
            val json = JSONObject(keysJson)
            json.keys().asSequence().toList().sorted()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun updateKeysJson(date: String, publicKey: String, privateKey: String): JSONObject {
        val existing = dataStore.data.map { it[PreferencesKeys.KEYS_JSON] }.first()
        val json = if (existing.isNullOrBlank()) JSONObject() else JSONObject(existing)
        val entry = JSONObject().apply {
            put("publicKey", publicKey)
            put("privateKey", privateKey)
        }
        json.put(date, entry)

        val dates = json.keys().asSequence().toList().sorted()
        if (dates.size > 7) {
            val toRemove = dates.take(dates.size - 7)
            toRemove.forEach { json.remove(it) }
        }
        return json
    }

    suspend fun addEnabledGroup(groupId: String) {
        dataStore.edit { preferences ->
            val currentGroups = preferences[PreferencesKeys.ENABLED_GROUPS] ?: emptySet()
            preferences[PreferencesKeys.ENABLED_GROUPS] = currentGroups + groupId
        }
    }

    suspend fun removeEnabledGroup(groupId: String) {
        dataStore.edit { preferences ->
            val currentGroups = preferences[PreferencesKeys.ENABLED_GROUPS] ?: emptySet()
            preferences[PreferencesKeys.ENABLED_GROUPS] = currentGroups - groupId
        }
    }

    suspend fun getEnabledGroups(): Set<String> = enabledGroups.first()

    suspend fun setLocationTrackingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCATION_TRACKING_ENABLED] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun isNotificationsEnabled(): Boolean = notificationsEnabled.first()

    suspend fun setSecureBackupEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SECURE_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun isSecureBackupEnabled(): Boolean = secureBackupEnabled.first()

    suspend fun clearUserData() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    suspend fun clearNickname() {
        dataStore.edit { preferences -> preferences.remove(PreferencesKeys.NICKNAME) }
    }
}


