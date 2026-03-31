package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.local.EncounterStore
import com.example.anonymousmeetup.data.model.Group
import com.example.anonymousmeetup.data.model.GroupCompatibilityReport
import com.example.anonymousmeetup.data.model.GroupJoinException
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val encounterStore: EncounterStore,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups

    private val _searchResults = MutableStateFlow<List<Group>>(emptyList())
    val searchResults: StateFlow<List<Group>> = _searchResults

    private val _searchDiagnostics = MutableStateFlow<Map<String, GroupCompatibilityReport>>(emptyMap())
    val searchDiagnostics: StateFlow<Map<String, GroupCompatibilityReport>> = _searchDiagnostics

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

    private val _joinSuccess = MutableStateFlow<String?>(null)
    val joinSuccess: StateFlow<String?> = _joinSuccess

    private val _createSuccess = MutableStateFlow<String?>(null)
    val createSuccess: StateFlow<String?> = _createSuccess

    private val _filter = MutableStateFlow("Мои")
    val filter: StateFlow<String> = _filter

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading

    private val _nearbyGroupHashes = MutableStateFlow<Set<String>>(emptySet())
    val nearbyGroupHashes: StateFlow<Set<String>> = _nearbyGroupHashes

    private var lastSearchQuery: String = ""
    private var groupsCollectorStarted = false

    val isLocationTrackingEnabled = userPreferences.isLocationTrackingEnabled

    init {
        loadGroups()
        observeNearbyGroups()
    }

    private fun observeNearbyGroups() {
        viewModelScope.launch {
            encounterStore.observeEncounters().collect { encounters ->
                val now = System.currentTimeMillis()
                val recent = encounters
                    .filter { now - it.happenedAt <= 6 * 60 * 60 * 1000 }
                    .mapNotNull { it.groupHash }
                    .toSet()
                _nearbyGroupHashes.value = recent
            }
        }
    }

    fun loadGroups() {
        if (groupsCollectorStarted) return
        groupsCollectorStarted = true
        viewModelScope.launch {
            _isLoading.value = true
            try {
                groupRepository.getLocalJoinedGroups().collect { groups ->
                    _groups.value = groups
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun setFilter(filter: String) {
        _filter.value = filter
    }

    fun searchGroups(query: String) {
        lastSearchQuery = query
        viewModelScope.launch {
            _isSearchLoading.value = true
            try {
                groupRepository.searchGroups(query).collect { groups ->
                    _searchResults.value = groups
                    _searchDiagnostics.value = groups.associate { it.id to groupRepository.inspectGroup(it) }
                    _isSearchLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isSearchLoading.value = false
            }
        }
    }

    fun compatibilityFor(group: Group): GroupCompatibilityReport {
        return searchDiagnostics.value[group.id] ?: groupRepository.inspectGroup(group)
    }

    fun createGroup(name: String, description: String, category: String, isPrivate: Boolean) {
        viewModelScope.launch {
            try {
                val groupId = groupRepository.createGroup(name, description, category, isPrivate)
                _error.value = null
                _createSuccess.value = groupId
            } catch (e: Exception) {
                val message = e.message ?: "неизвестная ошибка"
                _error.value = if (message.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "Ошибка создания группы: $message. Проверьте deploy firestore.rules и firestore.indexes.json на live backend."
                } else {
                    "Ошибка создания группы: $message"
                }
            }
        }
    }

    fun setLocationTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferences.setLocationTrackingEnabled(enabled)
            } catch (e: Exception) {
                _error.value = "Ошибка изменения статуса отслеживания: ${e.message}"
            }
        }
    }

    fun joinGroup(group: Group) {
        viewModelScope.launch {
            try {
                groupRepository.joinGroupLocally(group)
                _error.value = null
                _joinSuccess.value = group.id
            } catch (e: GroupJoinException) {
                _error.value = e.report.userMessage
            } catch (e: Exception) {
                _error.value = "Ошибка присоединения к группе: ${e.message}"
            }
        }
    }

    fun migrateGroup(group: Group) {
        viewModelScope.launch {
            try {
                val migrated = groupRepository.migrateLegacyGroup(group)
                _info.value = "Legacy группа переведена в новый формат"
                if (_searchResults.value.any { it.id == group.id }) {
                    searchGroups(lastSearchQuery)
                }
                if (_groups.value.any { it.id == group.id }) {
                    _groups.value = _groups.value.map { if (it.id == group.id) migrated else it }
                }
            } catch (e: Exception) {
                _error.value = "Не удалось мигрировать группу: ${e.message}"
            }
        }
    }

    fun clearJoinSuccess() {
        _joinSuccess.value = null
    }

    fun clearCreateSuccess() {
        _createSuccess.value = null
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            try {
                groupRepository.leaveGroupLocally(groupId)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Ошибка выхода из группы: ${e.message}"
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

