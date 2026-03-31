package com.example.anonymousmeetup.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.anonymousmeetup.data.model.MapMarker

@Composable
fun StaticMapScreen(
    apiKey: String,
    latitude: Double,
    longitude: Double,
    zoom: Int = 13,
    width: Int = 450,
    height: Int = 450,
    markers: List<MapMarker> = emptyList(),
    viewModel: StaticMapViewModel = hiltViewModel()
) {
    val mapState by viewModel.mapState.collectAsState()

    LaunchedEffect(apiKey, latitude, longitude, zoom, width, height, markers) {
        viewModel.loadMap(
            apiKey = apiKey,
            latitude = latitude,
            longitude = longitude,
            zoom = zoom,
            width = width,
            height = height,
            markers = markers
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (mapState) {
            StaticMapState.Loading -> {
                CircularProgressIndicator()
            }
            is StaticMapState.Success -> {
                val bitmap = (mapState as StaticMapState.Success).bitmap
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Статическая карта",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            is StaticMapState.Error -> {
                Text(text = (mapState as StaticMapState.Error).message)
            }
        }
    }
} 

