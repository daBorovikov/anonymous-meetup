package com.example.anonymousmeetup

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.anonymousmeetup.di.AppModule
import com.yandex.mapkit.MapKitFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AnonymousMeetupApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var injectedWorkManagerConfiguration: Configuration
    
    override val workManagerConfiguration: Configuration
        get() = injectedWorkManagerConfiguration
    
    override fun onCreate() {
        super.onCreate()
        try {
            // РРЅРёС†РёР°Р»РёР·Р°С†РёСЏ Yandex MapKit
            MapKitFactory.setApiKey("a37f1dc9-7d93-4206-964e-71f6788b236c")
            MapKitFactory.initialize(this)
            Log.d("AnonymousMeetup", "Yandex MapKit СѓСЃРїРµС€РЅРѕ РёРЅРёС†РёР°Р»РёР·РёСЂРѕРІР°РЅ")
        } catch (e: Exception) {
            Log.e("AnonymousMeetup", "РћС€РёР±РєР° РёРЅРёС†РёР°Р»РёР·Р°С†РёРё Yandex MapKit", e)
        }
    }
} 

