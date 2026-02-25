package com.example.emomap

import android.app.Application

class EmoMapApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize NetworkConfig with application context
        NetworkConfig.initialize(this)
    }
} 