package com.example.emomap

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.emomap.databinding.ActivityProfileBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ProfileActivity : BaseActivity() {

    private enum class EditableField {
        NAME,
        EMAIL,
        PASSWORD
    }

    private lateinit var binding: ActivityProfileBinding
    private lateinit var authRepository: AuthRepository
    private var currentUser: UserResponse? = null
    private var activeField: EditableField? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = AuthRepository(this)

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
        setSupportActionBar(binding.topBar.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupUI() {
        binding.topBar.btnProfile.setOnClickListener {
            // Already on profile screen.
        }
        setupBottomNavigation()
        setupClickListeners()
        setPasswordPlaceholder()
        disableAllFieldInputs()
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
        binding.btnEditName.setOnClickListener { enterEditMode(EditableField.NAME) }
        binding.btnEditEmail.setOnClickListener { enterEditMode(EditableField.EMAIL) }
        binding.btnEditPassword.setOnClickListener { enterEditMode(EditableField.PASSWORD) }

        binding.btnSaveName.setOnClickListener { saveNameField() }
        binding.btnSaveEmail.setOnClickListener { saveEmailField() }
        binding.btnSavePassword.setOnClickListener { savePasswordField() }

        binding.btnExportCsv.setOnClickListener { exportEmotionsCsv() }

        binding.btnLogout.setOnClickListener { showLogoutConfirmDialog() }
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
                            authRepository.logoutSync()
                            startActivity(Intent(this@ProfileActivity, LoginActivity::class.java))
                            finish()
                            return@launch
                        }
                        else -> "Failed to load profile: ${response.code()}"
                    }
                    Toast.makeText(this@ProfileActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoadingState(false)
                Toast.makeText(this@ProfileActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUI() {
        currentUser?.let { user ->
            binding.etName.setText(user.name ?: "")
            binding.etEmail.setText(user.email)
            setPasswordPlaceholder()
        }
    }

    private fun enterEditMode(field: EditableField) {
        if (activeField == field) return

        exitEditMode()
        activeField = field

        val editText = getEditText(field)
        getEditButton(field).visibility = View.GONE
        getSaveButton(field).visibility = View.VISIBLE

        editText.isEnabled = true
        editText.isFocusable = true
        editText.isFocusableInTouchMode = true
        editText.isCursorVisible = true

        if (field == EditableField.PASSWORD) {
            editText.text?.clear()
        }

        editText.requestFocus()
        editText.setSelection(editText.text?.length ?: 0)
    }

    private fun exitEditMode() {
        val previousField = activeField

        disableAllFieldInputs()
        binding.btnEditName.visibility = View.VISIBLE
        binding.btnEditEmail.visibility = View.VISIBLE
        binding.btnEditPassword.visibility = View.VISIBLE

        binding.btnSaveName.visibility = View.GONE
        binding.btnSaveEmail.visibility = View.GONE
        binding.btnSavePassword.visibility = View.GONE

        if (previousField == EditableField.EMAIL) {
            binding.etEmail.setText(currentUser?.email.orEmpty())
        }
        if (previousField == EditableField.PASSWORD) {
            setPasswordPlaceholder()
        }

        activeField = null
    }

    private fun disableAllFieldInputs() {
        binding.etName.isFocusable = false
        binding.etName.isFocusableInTouchMode = false
        binding.etName.isCursorVisible = false

        binding.etEmail.isFocusable = false
        binding.etEmail.isFocusableInTouchMode = false
        binding.etEmail.isCursorVisible = false

        binding.etPassword.isFocusable = false
        binding.etPassword.isFocusableInTouchMode = false
        binding.etPassword.isCursorVisible = false
    }

    private fun saveNameField() {
        val newName = binding.etName.text.toString().trim()
        if (newName.isEmpty()) {
            binding.etName.error = getString(R.string.name_hint)
            return
        }
        updateProfileName(newName)
    }

    private fun saveEmailField() {
        Toast.makeText(this, "Email editing is not supported by the backend yet", Toast.LENGTH_LONG).show()
        exitEditMode()
    }

    private fun savePasswordField() {
        Toast.makeText(this, "Password editing is not supported by the backend yet", Toast.LENGTH_LONG).show()
        exitEditMode()
    }

    private fun updateProfileName(name: String) {
        setLoadingState(true)

        lifecycleScope.launch {
            try {
                val request = ProfileUpdateRequest(name)
                val response = NetworkConfig.apiService.updateProfile(request)

                setLoadingState(false)

                if (response.isSuccessful) {
                    currentUser = response.body()
                    updateUI()
                    exitEditMode()
                    Toast.makeText(this@ProfileActivity, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ProfileActivity, "Failed to update profile", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoadingState(false)
                Toast.makeText(this@ProfileActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getEditButton(field: EditableField): ImageButton {
        return when (field) {
            EditableField.NAME -> binding.btnEditName
            EditableField.EMAIL -> binding.btnEditEmail
            EditableField.PASSWORD -> binding.btnEditPassword
        }
    }

    private fun getSaveButton(field: EditableField): MaterialButton {
        return when (field) {
            EditableField.NAME -> binding.btnSaveName
            EditableField.EMAIL -> binding.btnSaveEmail
            EditableField.PASSWORD -> binding.btnSavePassword
        }
    }

    private fun getEditText(field: EditableField): EditText {
        return when (field) {
            EditableField.NAME -> binding.etName
            EditableField.EMAIL -> binding.etEmail
            EditableField.PASSWORD -> binding.etPassword
        }
    }

    private fun setPasswordPlaceholder() {
        binding.etPassword.setText("********")
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
                        401 -> "Login required"
                        else -> "Export error: ${response.code()}"
                    }
                    Toast.makeText(this@ProfileActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
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

            Toast.makeText(this, "${getString(R.string.export_success)}\nFile: $fileName", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
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
                val success = authRepository.logout()

                setLoadingState(false)

                if (success) {
                    Toast.makeText(this@ProfileActivity, getString(R.string.logout_success), Toast.LENGTH_SHORT).show()
                }

                val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

            } catch (_: Exception) {
                setLoadingState(false)
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

        binding.btnEditName.isEnabled = !isLoading
        binding.btnEditEmail.isEnabled = !isLoading
        binding.btnEditPassword.isEnabled = !isLoading
        binding.btnSaveName.isEnabled = !isLoading
        binding.btnSaveEmail.isEnabled = !isLoading
        binding.btnSavePassword.isEnabled = !isLoading

        binding.btnExportCsv.isEnabled = !isLoading
        binding.btnLogout.isEnabled = !isLoading
    }
}
