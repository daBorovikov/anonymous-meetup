package com.example.anonymousmeetup.services

import android.content.Context
import android.util.Log
import com.example.anonymousmeetup.data.model.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.user_location.UserLocationLayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class MapService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
        
    private var initialized = false
    private var triedAutoInit = false

    fun initialize(apiKey: String) {
        if (!initialized) {
            try {
                // РџСЂРѕРІРµСЂСЏРµРј, Р±С‹Р» Р»Рё СѓР¶Рµ РёРЅРёС†РёР°Р»РёР·РёСЂРѕРІР°РЅ MapKitFactory
                try {
                    // Р•СЃР»Рё MapKitFactory СѓР¶Рµ РёРЅРёС†РёР°Р»РёР·РёСЂРѕРІР°РЅ, РІС‹Р·РѕРІ getInstance() РЅРµ РІС‹Р·РѕРІРµС‚ РёСЃРєР»СЋС‡РµРЅРёСЏ
                    MapKitFactory.getInstance()
                    initialized = true
                    Log.d("MapService", "MapKit СѓР¶Рµ РёРЅРёС†РёР°Р»РёР·РёСЂРѕРІР°РЅ")
                } catch (e: Exception) {
                    // Р•СЃР»Рё MapKitFactory РЅРµ РёРЅРёС†РёР°Р»РёР·РёСЂРѕРІР°РЅ, С‚РѕРіРґР° РёРЅРёС†РёР°Р»РёР·РёСЂСѓРµРј
                    MapKitFactory.setApiKey(apiKey)
                    MapKitFactory.initialize(context)
                    initialized = true
                    Log.d("MapService", "MapKit СѓСЃРїРµС€РЅРѕ РёРЅРёС†РёР°Р»РёР·РёСЂРѕРІР°РЅ")
                }
            } catch (e: Exception) {
                Log.e("MapService", "РћС€РёР±РєР° РёРЅРёС†РёР°Р»РёР·Р°С†РёРё MapKit", e)
            }
        }
    }
    
    fun onStop() {
        if (initialized) {
            try {
                MapKitFactory.getInstance().onStop()
            } catch (e: Exception) {
                Log.e("MapService", "РћС€РёР±РєР° РїСЂРё РІС‹Р·РѕРІРµ onStop", e)
            }
        }
    }

    suspend fun getCurrentLocation(): Location {
        return try {
            val location = getLastLocation()
            Location(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("MapService", "РћС€РёР±РєР° РїРѕР»СѓС‡РµРЅРёСЏ РјРµСЃС‚РѕРїРѕР»РѕР¶РµРЅРёСЏ", e)
            throw e
        }
    }

    private suspend fun getLastLocation() = suspendCancellableCoroutine { continuation ->
        try {
            val cancellationToken = object : CancellationToken() {
                override fun onCanceledRequested(listener: OnTokenCanceledListener) = 
                    CancellationTokenSource().token

                override fun isCancellationRequested() = false
            }

            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(location)
                    } else {
                        continuation.resumeWithException(Exception("РњРµСЃС‚РѕРїРѕР»РѕР¶РµРЅРёРµ РЅРµРґРѕСЃС‚СѓРїРЅРѕ"))
                    }
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        } catch (e: SecurityException) {
            continuation.resumeWithException(e)
        }
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth radius in meters
        val phi1 = lat1 * Math.PI / 180
        val phi2 = lat2 * Math.PI / 180
        val deltaPhi = (lat2 - lat1) * Math.PI / 180
        val deltaLambda = (lon2 - lon1) * Math.PI / 180

        val a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return r * c // distance in meters
    }

    fun createMapView(): MapView {
        ensureInitialized()
        return MapView(context).apply {
            mapWindow.map.move(
                CameraPosition(Point(55.751244, 37.618423), 11.0f, 0.0f, 0.0f)
            )
        }
    }

    fun updateCameraPosition(mapView: MapView, point: Point, zoom: Float = 15f) {
        mapView.mapWindow.map.move(
            CameraPosition(point, zoom, 0.0f, 0.0f)
        )
    }
    
    fun enableUserLocation(mapView: MapView, callback: ((UserLocationLayer) -> Unit)? = null) {
        if (!ensureInitialized()) {
            Log.e("MapService", "MapKit не инициализирован")
            return
        }
        
        try {
            val userLocationLayer = MapKitFactory.getInstance().createUserLocationLayer(mapView.mapWindow)
            userLocationLayer.isVisible = true
            userLocationLayer.isHeadingEnabled = true
            
            callback?.invoke(userLocationLayer)
        } catch (e: Exception) {
            Log.e("MapService", "РћС€РёР±РєР° РїСЂРё Р°РєС‚РёРІР°С†РёРё СЃР»РѕСЏ РјРµСЃС‚РѕРїРѕР»РѕР¶РµРЅРёСЏ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ", e)
        }
    }
    
    fun addPlacemark(
        mapView: MapView,
        latitude: Double,
        longitude: Double,
        title: String? = null,
        onTap: (() -> Unit)? = null
    ) {
        ensureInitialized()
        val point = Point(latitude, longitude)
        val mapObjects = mapView.mapWindow.map.mapObjects.addCollection()
        val placemark = mapObjects.addPlacemark(point)
        
        title?.let {
            placemark.setText(title)
        }
        onTap?.let { handler ->
            placemark.addTapListener { _, _ ->
                handler()
                true
            }
        }
    }

    private fun ensureInitialized(): Boolean {
        if (initialized) return true
        if (triedAutoInit) return false

        triedAutoInit = true
        return try {
            MapKitFactory.getInstance()
            initialized = true
            true
        } catch (e: Exception) {
            Log.e("MapService", "MapKit не инициализирован", e)
            false
        }
    }
} 

