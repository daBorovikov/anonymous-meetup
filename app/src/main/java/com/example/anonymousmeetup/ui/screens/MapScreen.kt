package com.example.anonymousmeetup.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.anonymousmeetup.ui.viewmodels.MapViewModel
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    groupId: String?,
    onNavigateBack: () -> Unit,
    onOpenPrivateChat: (String) -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val markers by viewModel.markers.collectAsState()
    val error by viewModel.error.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedSessionId by remember { mutableStateOf<String?>(null) }

    val mapView = remember { MapView(context) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    try {
                        MapKitFactory.getInstance().onStart()
                        mapView.onStart()
                    } catch (_: Exception) {
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    try {
                        mapView.onStop()
                        MapKitFactory.getInstance().onStop()
                    } catch (_: Exception) {
                    }
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                viewModel.mapService.enableUserLocation(mapView)
            } catch (_: Exception) {
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Карта") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = {
                    mapView.apply {
                        val initialPoint = Point(55.751244, 37.618423)
                        val initialCameraPosition = CameraPosition(initialPoint, 10f, 0f, 0f)
                        mapWindow.map.move(initialCameraPosition)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { map ->
                    map.mapWindow.map.mapObjects.clear()

                    markers.forEach { marker ->
                        viewModel.mapService.addPlacemark(
                            mapView = map,
                            latitude = marker.latitude,
                            longitude = marker.longitude,
                            title = marker.alias,
                            onTap = { selectedSessionId = marker.sessionId }
                        )
                    }

                    markers.firstOrNull()?.let {
                        map.mapWindow.map.move(
                            CameraPosition(Point(it.latitude, it.longitude), 12f, 0f, 0f),
                            Animation(Animation.Type.SMOOTH, 0.8f),
                            null
                        )
                    }
                }
            )

            if (markers.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Нет координат от приватных чатов",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Попросите собеседника отправить LOCATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            val selected = markers.firstOrNull { it.sessionId == selectedSessionId }
            if (selected != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(text = selected.alias, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${"%.5f".format(selected.latitude)}, ${"%.5f".format(selected.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = { onOpenPrivateChat(selected.sessionId) }) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Открыть чат")
                            }
                        }
                    }
                }
            }

            if (!error.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp, start = 16.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = error ?: "",
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
