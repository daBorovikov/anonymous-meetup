package com.example.anonymousmeetup.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.model.MapMarker
import com.example.anonymousmeetup.services.StaticMapService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StaticMapViewModel @Inject constructor(
    private val staticMapService: StaticMapService
) : ViewModel() {

    private val _mapState = MutableStateFlow<StaticMapState>(StaticMapState.Loading)
    val mapState: StateFlow<StaticMapState> = _mapState

    fun loadMap(
        apiKey: String,
        latitude: Double,
        longitude: Double,
        zoom: Int,
        width: Int,
        height: Int,
        markers: List<MapMarker>
    ) {
        viewModelScope.launch {
            _mapState.value = StaticMapState.Loading
            staticMapService.getStaticMap(
                apiKey = apiKey,
                latitude = latitude,
                longitude = longitude,
                zoom = zoom,
                width = width,
                height = height,
                markers = markers
            ).fold(
                onSuccess = { bitmap ->
                    _mapState.value = StaticMapState.Success(bitmap)
                },
                onFailure = { error ->
                    _mapState.value = StaticMapState.Error(error.message ?: "РќРµРёР·РІРµСЃС‚РЅР°СЏ РѕС€РёР±РєР°")
                }
            )
        }
    }
} 

