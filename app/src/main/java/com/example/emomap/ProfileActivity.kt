package com.example.emomap

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.emomap.databinding.ActivityProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : BaseActivity() {
    
    private lateinit var binding: ActivityProfileBinding
    private lateinit var authRepository: AuthRepository
    private var currentUser: UserResponse? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        authRepository = AuthRepository(this)
        
        // Check if user is logged in
        if (!authRepository.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        setupToolbar()
        setupUI()
        loadUserProfile()
    }
    
    override fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }
    
    private fun setupUI() {
        setupBottomNavigation()
        setupClickListeners()
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_map -> {
                    startActivity(Intent(this, MapActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }
        
        binding.btnExportCsv.setOnClickListener {
            exportEmotionsCsv()
        }
        
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmDialog()
        }
        
        binding.switchShareEmotions.setOnCheckedChangeListener { _, isChecked ->
            // TODO: Save privacy setting to backend
            Toast.makeText(this, "Настройка конфиденциальности ${if (isChecked) "включена" else "отключена"}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadUserProfile() {
        setLoadingState(true)
        
        lifecycleScope.launch {
            try {
                val response = NetworkConfig.apiService.getCurrentUser()
                
                setLoadingState(false)
                
                if (response.isSuccessful) {
                    currentUser = response.body()
                    updateUI()
                } else {
                    val errorMessage = when (response.code()) {
                        401 -> {
                            // Session expired, redirect to login
                            authRepository.logoutSync()
                            startActivity(Intent(this@ProfileActivity, LoginActivity::class.java))
                            finish()
                            return@launch
                        }
                        else -> "Ошибка загрузки профиля: ${response.code()}"
                    }
                    Toast.makeText(this@ProfileActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoadingState(false)
                Toast.makeText(this@ProfileActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateUI() {
        currentUser?.let { user ->
            binding.tvUserName.text = user.name ?: "Пользователь"
            binding.tvUserEmail.text = user.email
        }
    }
    
    private fun showEditProfileDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.name_hint)
            setText(currentUser?.name ?: "")
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_profile))
            .setView(editText)
            .setPositiveButton(getString(R.string.save_changes)) { _, _ ->
                val newName = editText.text.toString().trim()
                updateProfile(newName)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun updateProfile(name: String) {
        setLoadingState(true)
        
        lifecycleScope.launch {
            try {
                val request = ProfileUpdateRequest(name.takeIf { it.isNotEmpty() })
                val response = NetworkConfig.apiService.updateProfile(request)
                
                setLoadingState(false)
                
                if (response.isSuccessful) {
                    currentUser = response.body()
                    updateUI()
                    Toast.makeText(this@ProfileActivity, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ProfileActivity, "Ошибка обновления профиля", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoadingState(false)
                Toast.makeText(this@ProfileActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun exportEmotionsCsv() {
        setLoadingState(true)
        Toast.makeText(this, getString(R.string.export_started), Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val response = NetworkConfig.apiService.exportEmotionsCSV()
                
                setLoadingState(false)
                
                if (response.isSuccessful) {
                    response.body()?.let { responseBody ->
                        saveCsvFile(responseBody.bytes())
                    } ?: run {
                        Toast.makeText(this@ProfileActivity, getString(R.string.export_error), Toast.LENGTH_LONG).show()
                    }
                } else {
                    val errorMessage = when (response.code()) {
                        401 -> "Необходимо войти в систему"
                        else -> "Ошибка экспорта: ${response.code()}"
                    }
                    Toast.makeText(this@ProfileActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoadingState(false)
                Toast.makeText(this@ProfileActivity, getString(R.string.export_error), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun saveCsvFile(csvData: ByteArray) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val fileName = "emomap_emotions_${System.currentTimeMillis()}.csv"
            val file = File(downloadsDir, fileName)
            
            FileOutputStream(file).use { output ->
                output.write(csvData)
            }
            
            Toast.makeText(this, "${getString(R.string.export_success)}\nФайл: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_error), Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showLogoutConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.logout_confirm))
            .setPositiveButton(getString(R.string.logout)) { _, _ ->
                performLogout()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun performLogout() {
        setLoadingState(true)
        
        lifecycleScope.launch {
            try {
                // Call logout API
                val success = authRepository.logout()
                
                setLoadingState(false)
                
                if (success) {
                    Toast.makeText(this@ProfileActivity, getString(R.string.logout_success), Toast.LENGTH_SHORT).show()
                }
                
                // Redirect to login regardless of API response
                val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                
            } catch (e: Exception) {
                setLoadingState(false)
                // Still logout locally even if API call fails
                authRepository.clearSessionId()
                
                val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }
    
    private fun setLoadingState(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnEditProfile.isEnabled = !isLoading
        binding.btnExportCsv.isEnabled = !isLoading
        binding.btnLogout.isEnabled = !isLoading
    }
} 