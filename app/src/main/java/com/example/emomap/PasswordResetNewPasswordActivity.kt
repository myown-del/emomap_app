package com.example.emomap

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.emomap.databinding.ActivityPasswordResetNewPasswordBinding
import kotlinx.coroutines.launch

/**
 * Step 3 of password reset: set new password.
 */
class PasswordResetNewPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordResetNewPasswordBinding
    private lateinit var authRepository: AuthRepository

    private lateinit var code: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPasswordResetNewPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = AuthRepository(this)
        code = intent.getStringExtra(EXTRA_CODE).orEmpty()

        setupListeners()
    }

    private fun setupListeners() {
        binding.tvBack.setOnClickListener {
            finish()
        }

        binding.etNewPassword.addTextChangedListener(SimpleTextWatcher {
            val password = binding.etNewPassword.text?.toString().orEmpty()
            binding.btnConfirm.isEnabled = password.isNotEmpty()
        })

        binding.btnConfirm.setOnClickListener {
            val newPassword = binding.etNewPassword.text?.toString().orEmpty()
            if (!validatePassword(newPassword)) return@setOnClickListener
            confirmNewPassword(newPassword)
        }
    }

    private fun validatePassword(password: String): Boolean {
        binding.tilNewPassword.error = null

        return when {
            password.isEmpty() -> {
                binding.tilNewPassword.error = getString(R.string.error_empty_password)
                false
            }

            password.length < 6 -> {
                binding.tilNewPassword.error = getString(R.string.error_password_too_short)
                false
            }

            else -> true
        }
    }

    private fun confirmNewPassword(newPassword: String) {
        setLoading(true)

        lifecycleScope.launch {
            when (val result = authRepository.confirmPasswordReset(code, newPassword)) {
                is OperationResult.Success -> {
                    setLoading(false)
                    Toast.makeText(
                        this@PasswordResetNewPasswordActivity,
                        result.message,
                        Toast.LENGTH_SHORT
                    ).show()

                    // Back to login screen after successful reset
                    val intent = Intent(this@PasswordResetNewPasswordActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }

                is OperationResult.Error -> {
                    setLoading(false)
                    Toast.makeText(
                        this@PasswordResetNewPasswordActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnConfirm.isEnabled = !isLoading && binding.etNewPassword.text?.isNotEmpty() == true
        binding.etNewPassword.isEnabled = !isLoading
        binding.tvBack.isEnabled = !isLoading
    }

    companion object {
        const val EXTRA_CODE = "extra_code"
    }
}


