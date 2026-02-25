package com.example.emomap

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    protected open fun setupToolbar() {
        // Override in child activities to set up toolbar
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_profile -> {
                // Don't navigate if already on profile page
                if (this !is ProfileActivity) {
                    startActivity(Intent(this, ProfileActivity::class.java))
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
} 