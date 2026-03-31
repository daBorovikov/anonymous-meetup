package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.model.LocalMessageRecord
import com.example.anonymousmeetup.data.repository.GroupChatRepository
import com.example.anonymousmeetup.data.repository.GroupRepository
import com.example.anonymousmeetup.data.repository.PrivateChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val groupChatRepository: GroupChatRepository,
    private val groupRepository: GroupRepository,
    private val privateChatRepository: PrivateChatRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages

    private val _groupTitle = MutableStateFlow("Чат группы")
    val groupTitle: StateFlow<String> = _groupTitle

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadMessages(groupId: String) {
        viewModelScope.launch {
            try {
                val group = groupRepository.getGroup(groupId)
                _groupTitle.value = sanitizeDisplayText(group.name, fallback = "Чат группы")
                groupChatRepository.observeGroupMessages(groupId).collect { records ->
                    _messages.value = records.map { it.toUiMessage() }.sortedBy { it.timestamp }
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun sendMessage(groupId: String, text: String) {
        viewModelScope.launch {
            try {
                groupChatRepository.sendGroupMessage(groupId, text)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun startPrivateChat(targetAlias: String, targetPublicKey: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = privateChatRepository.startPrivateChat(targetPublicKey, targetAlias)
            result
                .onSuccess {
                    _error.value = null
                    onResult(it)
                }
                .onFailure {
                    _error.value = "Ошибка создания приватного канала: ${it.message}"
                    onResult(null)
                }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun setError(message: String) {
        _error.value = message
    }

    private fun LocalMessageRecord.toUiMessage(): UiMessage {
        val rawJson = rawPayloadJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val alias = rawJson?.optString("senderAlias").orEmpty().ifBlank {
            if (direction.name == "OUTGOING") "Вы" else "Участник"
        }
        return UiMessage(
            id = localMessageId,
            senderAlias = sanitizeDisplayText(
                value = alias,
                fallback = if (direction.name == "OUTGOING") "Вы" else "Участник"
            ),
            text = text ?: "",
            timestamp = timestamp,
            isMine = direction.name == "OUTGOING"
        )
    }

    private fun sanitizeDisplayText(value: String?, fallback: String): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return fallback
        if (trimmed.contains('�')) return fallback
        if (trimmed.count { it == 'Ð' || it == 'Ñ' } >= 2) return fallback
        return trimmed
    }

    data class UiMessage(
        val id: String,
        val senderAlias: String,
        val text: String,
        val timestamp: Long,
        val isMine: Boolean
    )
}
