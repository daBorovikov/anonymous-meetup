package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.model.LocalConversation
import com.example.anonymousmeetup.data.model.SessionStatus
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.repository.DebugToolsRepository
import com.example.anonymousmeetup.data.repository.PrivateChatRepository
import com.example.anonymousmeetup.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences,
    private val privateChatRepository: PrivateChatRepository,
    private val debugToolsRepository: DebugToolsRepository
) : ViewModel() {

    val nickname = userPreferences.nickname
    val isLocationTrackingEnabled = userPreferences.isLocationTrackingEnabled
    val notificationsEnabled = userPreferences.notificationsEnabled
    val secureBackupEnabled = userPreferences.secureBackupEnabled
    val debugTraces = debugToolsRepository.traces()

    private val _publicKey = MutableStateFlow<String?>(null)
    val publicKey: StateFlow<String?> = _publicKey

    private val _keyDate = MutableStateFlow<String?>(null)
    val keyDate: StateFlow<String?> = _keyDate

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

    private val _backupPayload = MutableStateFlow<String?>(null)
    val backupPayload: StateFlow<String?> = _backupPayload

    private val _isDebugBusy = MutableStateFlow(false)
    val isDebugBusy: StateFlow<Boolean> = _isDebugBusy

    private val _conversations = MutableStateFlow<List<PrivateConversationPreview>>(emptyList())
    val conversations: StateFlow<List<PrivateConversationPreview>> = _conversations

    init {
        viewModelScope.launch {
            try {
                val identity = userRepository.ensureIdentityKeys()
                _publicKey.value = identity.publicKey
                _keyDate.value = "identity"
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
        viewModelScope.launch {
            privateChatRepository.observeConversations().collect { items ->
                _conversations.value = items.map { it.toPreview() }
            }
        }
    }

    fun setLocationTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferences.setLocationTrackingEnabled(enabled)
            } catch (e: Exception) {
                _error.value = "Ошибка изменения статуса: ${e.message}"
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferences.setNotificationsEnabled(enabled)
            } catch (e: Exception) {
                _error.value = "Ошибка изменения уведомлений: ${e.message}"
            }
        }
    }

    fun setSecureBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferences.setSecureBackupEnabled(enabled)
            } catch (e: Exception) {
                _error.value = "Ошибка изменения secure backup: ${e.message}"
            }
        }
    }

    fun logout() {
        userRepository.logout()
        viewModelScope.launch {
            userPreferences.clearUserData()
        }
    }

    fun updateNickname(newNickname: String) {
        viewModelScope.launch {
            try {
                userRepository.updateLogin(newNickname)
                _info.value = "Ник обновлён"
            } catch (e: Exception) {
                _error.value = "Не удалось обновить ник: ${e.message}"
            }
        }
    }

    fun exportBackup(password: String) {
        viewModelScope.launch {
            runCatching { userRepository.exportBackup(password) }
                .onSuccess {
                    _backupPayload.value = it
                    _info.value = "Backup создан"
                }
                .onFailure {
                    _error.value = "Ошибка экспорта: ${it.message}"
                }
        }
    }

    fun importBackup(data: String, password: String) {
        viewModelScope.launch {
            runCatching { userRepository.importBackup(data, password) }
                .onSuccess {
                    val identity = userRepository.ensureIdentityKeys()
                    _publicKey.value = identity.publicKey
                    _keyDate.value = "identity"
                    _info.value = "Backup успешно импортирован"
                }
                .onFailure {
                    _error.value = "Ошибка импорта: ${it.message}"
                }
        }
    }

    fun startPrivateChat(peerPublicKey: String, localAlias: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            privateChatRepository.startPrivateChat(peerPublicKey, localAlias)
                .onSuccess { onResult(it) }
                .onFailure {
                    _error.value = "Не удалось создать приватный чат: ${it.message}"
                    onResult(null)
                }
        }
    }

    fun resetLocalAnonymousState() {
        runDebugAction("Локальное anonymous state очищено") {
            debugToolsRepository.resetLocalAnonymousState()
        }
    }

    fun clearLegacyCaches() {
        runDebugAction("Legacy кэши очищены") {
            debugToolsRepository.clearLegacyCaches()
        }
    }

    fun resyncGroupsFromServer() {
        runDebugAction { _info.value = "Обновлено групп: ${debugToolsRepository.resyncGroupsFromServer()}" }
    }

    fun runGroupVerificationScenario() {
        runDebugAction { _info.value = debugToolsRepository.runGroupVerificationScenario() }
    }

    fun clearTraceLog() {
        debugToolsRepository.clearTraceLog()
        _info.value = "Trace log очищен"
    }

    private fun runDebugAction(successMessage: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            _isDebugBusy.value = true
            runCatching { block() }
                .onSuccess {
                    successMessage?.let { _info.value = it }
                }
                .onFailure {
                    _error.value = it.message ?: "Debug action failed"
                }
            _isDebugBusy.value = false
        }
    }

    fun clearInfo() { _info.value = null }
    fun clearError() { _error.value = null }
    fun consumeBackupPayload() { _backupPayload.value = null }

    private fun LocalConversation.toPreview(): PrivateConversationPreview {
        return PrivateConversationPreview(
            conversationId = conversationId,
            title = sanitizeDisplayText(localAlias, "Анонимный чат"),
            status = sessionStatus,
            subtitle = when {
                sessionStatus == SessionStatus.PENDING && !isInitiator -> "Входящее приглашение ждёт вашего решения"
                sessionStatus == SessionStatus.PENDING && isInitiator -> "Приглашение отправлено, ждём ответа"
                sessionStatus == SessionStatus.ACCEPTED -> "Канал готов к переписке"
                sessionStatus == SessionStatus.ACTIVE -> "Есть активная история сообщений"
                sessionStatus == SessionStatus.REJECTED -> "Приглашение было отклонено"
                else -> "Состояние: ${sessionStatus.name.lowercase()}"
            },
            isPendingIncoming = sessionStatus == SessionStatus.PENDING && !isInitiator
        )
    }

    private fun sanitizeDisplayText(value: String?, fallback: String): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return fallback
        if (trimmed.contains('�')) return fallback
        if (trimmed.count { it == 'Ð' || it == 'Ñ' } >= 2) return fallback
        return trimmed
    }

    data class PrivateConversationPreview(
        val conversationId: String,
        val title: String,
        val status: SessionStatus,
        val subtitle: String,
        val isPendingIncoming: Boolean
    )
}
