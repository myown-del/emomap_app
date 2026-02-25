package com.example.emomap

import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    protected open fun setupToolbar() {
        // Override in child activities to set up toolbar
    }
}
