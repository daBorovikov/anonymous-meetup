package com.example.anonymousmeetup.ui.map

import android.graphics.Bitmap

sealed class StaticMapState {
    object Loading : StaticMapState()
    data class Success(val bitmap: Bitmap) : StaticMapState()
    data class Error(val message: String) : StaticMapState()
} 

