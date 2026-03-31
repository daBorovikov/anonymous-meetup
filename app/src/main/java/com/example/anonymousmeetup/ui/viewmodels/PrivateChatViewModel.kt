package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.model.LocalConversation
import com.example.anonymousmeetup.data.model.LocationPayload
import com.example.anonymousmeetup.data.model.PrivateMessageUiModel
import com.example.anonymousmeetup.data.repository.PrivateChatRepository
import com.example.anonymousmeetup.services.MapService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class PrivateChatViewModel @Inject constructor(
    private val privateChatRepository: PrivateChatRepository,
    private val mapService: MapService
) : ViewModel() {

    private val _session = MutableStateFlow<LocalConversation?>(null)
    val session: StateFlow<LocalConversation?> = _session

    private val _messages = MutableStateFlow<List<PrivateMessageUiModel>>(emptyList())
    val messages: StateFlow<List<PrivateMessageUiModel>> = _messages

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

    fun bindConversation(conversationId: String) {
        viewModelScope.launch {
            privateChatRepository.observeConversation(conversationId).collect { conversation ->
                _session.value = conversation
            }
        }
        viewModelScope.launch {
            privateChatRepository.observeMessages(conversationId).collect { records ->
                _messages.value = records.map { record ->
                    val rawJson = record.rawPayloadJson?.let { runCatching { JSONObject(it) }.getOrNull() }
                    val location = if (record.type.name == "LOCATION" && rawJson != null) {
                        LocationPayload(
                            displayName = rawJson.optString("senderAlias").ifBlank { "Собеседник" },
                            latitude = rawJson.optDouble("latitude"),
                            longitude = rawJson.optDouble("longitude"),
                            sentAt = rawJson.optLong("sentAt", record.timestamp)
                        )
                    } else {
                        null
                    }
                    PrivateMessageUiModel(
                        id = record.localMessageId,
                        text = record.text.orEmpty(),
                        timestamp = record.timestamp,
                        type = record.type.name,
                        isMine = record.direction.name == "OUTGOING",
                        location = location
                    )
                }.sortedBy { it.timestamp }
            }
        }
    }

    fun sendText(text: String) {
        val conversationId = _session.value?.conversationId ?: return
        viewModelScope.launch {
            runCatching {
                privateChatRepository.sendPrivateText(conversationId, text)
            }.onFailure {
                _error.value = "Send failed: ${it.message}"
            }
        }
    }

    fun sendLocation() {
        val conversationId = _session.value?.conversationId ?: return
        viewModelScope.launch {
            runCatching {
                val location = mapService.getCurrentLocation()
                val payload = LocationPayload(
                    displayName = "Я",
                    latitude = location.latitude,
                    longitude = location.longitude,
                    sentAt = System.currentTimeMillis()
                )
                privateChatRepository.sendLocation(conversationId, payload)
            }.onFailure {
                _error.value = "Failed to send location: ${it.message}"
            }
        }
    }

    fun clearInfo() {
        _info.value = null
    }

    fun clearError() {
        _error.value = null
    }
}
