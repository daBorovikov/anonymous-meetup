package com.example.anonymousmeetup.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isAuthorized = MutableStateFlow(false)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized

    fun register(login: String, password: String) {
        viewModelScope.launch {
            try {
                Log.d("LoginViewModel", "Регистрация: $login")
                userRepository.register(login, password)
                _isAuthorized.value = true
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Ошибка регистрации", e)
                when (e) {
                    is FirebaseFirestoreException -> {
                        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                            _error.value = "Нет подключения к интернету. Проверьте соединение и попробуйте снова."
                        } else {
                            _error.value = "Ошибка регистрации: ${e.message}"
                        }
                    }
                    else -> {
                        _error.value = "Ошибка регистрации: ${e.message}"
                    }
                }
            }
        }
    }
}



