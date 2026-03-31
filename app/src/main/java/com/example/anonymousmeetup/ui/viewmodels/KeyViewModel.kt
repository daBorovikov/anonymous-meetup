package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _publicKey = MutableStateFlow<String?>(null)
    val publicKey: StateFlow<String?> = _publicKey

    private val _keyDate = MutableStateFlow<String?>(null)
    val keyDate: StateFlow<String?> = _keyDate

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            try {
                val keyInfo = userRepository.ensureDailyKeys()
                _publicKey.value = keyInfo.publicKey
                _keyDate.value = keyInfo.keyDate
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}


