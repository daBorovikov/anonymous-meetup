package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.model.Group
import com.example.anonymousmeetup.data.model.GroupCompatibilityReport
import com.example.anonymousmeetup.data.model.GroupJoinException
import com.example.anonymousmeetup.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _group = MutableStateFlow<Group?>(null)
    val group: StateFlow<Group?> = _group

    private val _compatibility = MutableStateFlow<GroupCompatibilityReport?>(null)
    val compatibility: StateFlow<GroupCompatibilityReport?> = _compatibility

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

    private val _isMember = MutableStateFlow(false)
    val isMember: StateFlow<Boolean> = _isMember

    fun loadGroup(groupId: String) {
        viewModelScope.launch {
            try {
                val group = groupRepository.getGroup(groupId)
                _group.value = group
                _compatibility.value = groupRepository.inspectGroup(group)
                _isMember.value = groupRepository.isGroupJoined(group.id)
            } catch (e: Exception) {
                _error.value = "Ошибка загрузки группы: ${e.message}"
            }
        }
    }

    fun joinGroup(groupId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val group = groupRepository.getGroup(groupId)
                groupRepository.joinGroupLocally(group)
                _group.value = groupRepository.getGroup(groupId)
                _compatibility.value = _group.value?.let { groupRepository.inspectGroup(it) }
                _isMember.value = true
                onDone()
            } catch (e: GroupJoinException) {
                _error.value = e.report.userMessage
            } catch (e: Exception) {
                _error.value = "Не удалось вступить в группу: ${e.message}"
            }
        }
    }

    fun migrateGroup(groupId: String) {
        viewModelScope.launch {
            try {
                val migrated = groupRepository.migrateLegacyGroup(groupId)
                _group.value = migrated
                _compatibility.value = groupRepository.inspectGroup(migrated)
                _info.value = "Группа переведена в новый формат"
            } catch (e: Exception) {
                _error.value = "Не удалось мигрировать группу: ${e.message}"
            }
        }
    }

    fun leaveGroup(groupId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                groupRepository.leaveGroupLocally(groupId)
                _isMember.value = false
                onDone()
            } catch (e: Exception) {
                _error.value = "Не удалось выйти из группы: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearInfo() {
        _info.value = null
    }
}

