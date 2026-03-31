package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.model.LocalConversation
import com.example.anonymousmeetup.data.model.PrivateMessageUiModel
import com.example.anonymousmeetup.data.repository.PrivateChatRepository
import com.example.anonymousmeetup.services.MapService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val privateChatRepository: PrivateChatRepository,
    val mapService: MapService
) : ViewModel() {

    private val _markers = MutableStateFlow<List<PeerMarker>>(emptyList())
    val markers: StateFlow<List<PeerMarker>> = _markers

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var observeJob: Job? = null
    private var markersJob: Job? = null

    init {
        observePrivateLocations()
    }

    private fun observePrivateLocations() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            privateChatRepository.observeConversations().collect { conversations ->
                if (conversations.isEmpty()) {
                    markersJob?.cancel()
                    _markers.value = emptyList()
                    return@collect
                }
                subscribeToConversationMessages(conversations)
            }
        }
    }

    private fun subscribeToConversationMessages(conversations: List<LocalConversation>) {
        markersJob?.cancel()
        markersJob = viewModelScope.launch {
            val flows = conversations.map { conversation ->
                privateChatRepository.observeMessages(conversation.conversationId)
            }
            if (flows.isEmpty()) {
                _markers.value = emptyList()
                return@launch
            }
            if (flows.size == 1) {
                flows.first().collect { records ->
                    _markers.value = extractMarkers(conversations, listOf(records))
                }
                return@launch
            }
            combine(flows) { arrays -> arrays.map { it.toList() } }.collect { grouped ->
                _markers.value = extractMarkers(conversations, grouped)
            }
        }
    }

    private fun extractMarkers(
        conversations: List<LocalConversation>,
        groupedMessages: List<List<com.example.anonymousmeetup.data.model.LocalMessageRecord>>
    ): List<PeerMarker> {
        val markers = mutableListOf<PeerMarker>()
        conversations.forEachIndexed { index, conversation ->
            val latestLocation = groupedMessages
                .getOrNull(index)
                .orEmpty()
                .mapNotNull { record ->
                    if (record.type.name != "LOCATION" || record.rawPayloadJson.isNullOrBlank()) return@mapNotNull null
                    val json = runCatching { org.json.JSONObject(record.rawPayloadJson) }.getOrNull() ?: return@mapNotNull null
                    PrivateMessageUiModel(
                        id = record.localMessageId,
                        text = record.text.orEmpty(),
                        timestamp = record.timestamp,
                        type = record.type.name,
                        isMine = record.direction.name == "OUTGOING",
                        location = com.example.anonymousmeetup.data.model.LocationPayload(
                            displayName = json.optString("senderAlias").ifBlank { conversation.localAlias ?: "Собеседник" },
                            latitude = json.optDouble("latitude"),
                            longitude = json.optDouble("longitude"),
                            sentAt = json.optLong("sentAt", record.timestamp)
                        )
                    )
                }
                .maxByOrNull { it.timestamp }
                ?.location

            if (latestLocation != null) {
                markers.add(
                    PeerMarker(
                        sessionId = conversation.conversationId,
                        alias = conversation.localAlias ?: latestLocation.displayName,
                        latitude = latestLocation.latitude,
                        longitude = latestLocation.longitude,
                        timestamp = latestLocation.sentAt
                    )
                )
            }
        }
        return markers.sortedByDescending { it.timestamp }
    }

    override fun onCleared() {
        super.onCleared()
        markersJob?.cancel()
        mapService.onStop()
    }

    data class PeerMarker(
        val sessionId: String,
        val alias: String,
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long
    )
}
