package com.example.emomap

import android.app.Application
import org.maplibre.android.MapLibre

class EmoMapApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()

        MapLibre.getInstance(this)

        // Initialize NetworkConfig with application context
        NetworkConfig.initialize(this)
    }
} 
