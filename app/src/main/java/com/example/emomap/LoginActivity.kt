package com.example.emomap

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.emomap.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var authRepository: AuthRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        authRepository = AuthRepository(this)
        
        // Check if already logged in
        if (authRepository.isLoggedIn()) {
            navigateToMainActivity()
            return
        }
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            performLogin()
        }
        
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, PasswordResetEmailActivity::class.java))
        }
        
        binding.tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
    
    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        
        // Validate inputs
        if (!validateInputs(email, password)) {
            return
        }
        
        // Show loading
        setLoadingState(true)
        
        // Perform login
        lifecycleScope.launch {
            val result = authRepository.login(email, password)
            
            setLoadingState(false)
            
            when (result) {
                is AuthResult.Success -> {
                    Toast.makeText(this@LoginActivity, "Успешный вход!", Toast.LENGTH_SHORT).show()
                    navigateToMainActivity()
                }
                is AuthResult.Error -> {
                    Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true
        
        // Clear previous errors
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        
        // Validate email
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_empty_email)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_invalid_email)
            isValid = false
        }
        
        // Validate password
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_empty_password)
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_password_too_short)
            isValid = false
        }
        
        return isValid
    }
    
    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false
            binding.etEmail.isEnabled = false
            binding.etPassword.isEnabled = false
            binding.tvForgotPassword.isEnabled = false
            binding.tvRegisterLink.isEnabled = false
        } else {
            binding.progressBar.visibility = View.GONE
            binding.btnLogin.isEnabled = true
            binding.etEmail.isEnabled = true
            binding.etPassword.isEnabled = true
            binding.tvForgotPassword.isEnabled = true
            binding.tvRegisterLink.isEnabled = true
        }
    }
    
    private fun navigateToMainActivity() {
        Toast.makeText(this, "Вход выполнен успешно! Добро пожаловать в EmoMap!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
} 
