package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.model.LocalContact
import com.example.anonymousmeetup.data.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val socialRepository: SocialRepository
) : ViewModel() {

    private val _friends = MutableStateFlow<List<LocalContact>>(emptyList())
    val friends: StateFlow<List<LocalContact>> = _friends

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            try {
                socialRepository.getFriends().collect { items ->
                    _friends.value = items
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
