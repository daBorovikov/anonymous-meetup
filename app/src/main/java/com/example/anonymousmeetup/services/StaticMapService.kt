package com.example.anonymousmeetup.services

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.anonymousmeetup.data.model.MapMarker
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaticMapService @Inject constructor(
    private val context: Context
) {
    fun createMapView(): MapView {
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

    // РЎРєСЂРёРЅС€РѕС‚ РєР°СЂС‚С‹ РЅРµ РїРѕРґРґРµСЂР¶РёРІР°РµС‚СЃСЏ РЅР°РїСЂСЏРјСѓСЋ РІ MapKit РґР»СЏ Android
    // fun takeScreenshot(mapView: MapView): Bitmap? {
    //     return mapView.mapWindow.screenshot()
    // }

    // Р’ Yandex MapKit РґР»СЏ Android РЅРµС‚ РїСЂСЏРјРѕРіРѕ РјРµС‚РѕРґР° РїРѕР»СѓС‡РµРЅРёСЏ СЃС‚Р°С‚РёС‡РµСЃРєРѕР№ РєР°СЂС‚С‹
    // Р­С‚Рѕ РїСЂРѕСЃС‚Рѕ Р·Р°РіР»СѓС€РєР°, РєРѕС‚РѕСЂР°СЏ РІ СЂРµР°Р»СЊРЅРѕРј РїСЂРёР»РѕР¶РµРЅРёРё РґРѕР»Р¶РЅР° Р±С‹С‚СЊ Р·Р°РјРµРЅРµРЅР°
    // РЅР° СЂР°Р±РѕС‚Сѓ СЃ Yandex Static API РёР»Рё РґСЂСѓРіРёРј СЃРµСЂРІРёСЃРѕРј СЃС‚Р°С‚РёС‡РµСЃРєРёС… РєР°СЂС‚
    suspend fun getStaticMap(
        apiKey: String,
        latitude: Double,
        longitude: Double,
        zoom: Int,
        width: Int,
        height: Int,
        markers: List<MapMarker>
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            // Р—Р°РіР»СѓС€РєР°: РІРѕР·РІСЂР°С‰Р°РµРј РїСѓСЃС‚СѓСЋ Р±РёС‚РјР°РїСѓ РЅСѓР¶РЅРѕРіРѕ СЂР°Р·РјРµСЂР°
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Result.success(bitmap)
            
            // Р’ СЂРµР°Р»СЊРЅРѕРј РїСЂРёР»РѕР¶РµРЅРёРё Р·РґРµСЃСЊ РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РєРѕРґ РґР»СЏ РїРѕР»СѓС‡РµРЅРёСЏ СЃС‚Р°С‚РёС‡РµСЃРєРѕР№ РєР°СЂС‚С‹
            // РЅР°РїСЂРёРјРµСЂ С‡РµСЂРµР· HTTP Р·Р°РїСЂРѕСЃ Рє Static Maps API
        } catch (e: IOException) {
            Log.e("StaticMapService", "Error fetching static map", e)
            Result.failure(e)
        }
    }
} 

