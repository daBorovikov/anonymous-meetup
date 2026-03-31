package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _isAuthorized = MutableStateFlow(false)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            val isLoggedIn = !userPreferences.getNickname().isNullOrBlank()
            _isAuthorized.value = isLoggedIn
            if (isLoggedIn) {
                try {
                    userRepository.ensureDailyKeys()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun login(login: String, password: String) {
        viewModelScope.launch {
            try {
                userRepository.login(login, password)
                _isAuthorized.value = true
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun register(login: String, password: String) {
        viewModelScope.launch {
            try {
                userRepository.register(login, password)
                _isAuthorized.value = true
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            userPreferences.clearNickname()
            _isAuthorized.value = false
            userRepository.logout()
        }
    }
}


