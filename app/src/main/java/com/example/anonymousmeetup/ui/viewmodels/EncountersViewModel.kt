package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.model.EncounterLocal
import com.example.anonymousmeetup.data.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EncountersViewModel @Inject constructor(
    private val socialRepository: SocialRepository
) : ViewModel() {

    private val _encounters = MutableStateFlow<List<EncounterLocal>>(emptyList())
    val encounters: StateFlow<List<EncounterLocal>> = _encounters

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            try {
                socialRepository.getEncounters().collect { items ->
                    _encounters.value = items
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
