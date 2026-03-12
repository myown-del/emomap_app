package com.example.emomap

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.emomap.databinding.ActivityPasswordResetEmailBinding
import kotlinx.coroutines.launch

/**
 * Step 1 of password reset: request reset code by email.
 */
class PasswordResetEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordResetEmailBinding
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPasswordResetEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = AuthRepository(this)

        setupListeners()
    }

    private fun setupListeners() {
        binding.tvBack.setOnClickListener {
            finish()
        }

        binding.etEmail.addTextChangedListener(SimpleTextWatcher {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            binding.btnNext.isEnabled = email.isNotEmpty()
        })

        binding.btnNext.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            if (!validateEmail(email)) return@setOnClickListener
            requestReset(email)
        }
    }

    private fun validateEmail(email: String): Boolean {
        binding.tilEmail.error = null

        return when {
            email.isEmpty() -> {
                binding.tilEmail.error = getString(R.string.error_empty_email)
                false
            }

            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.tilEmail.error = getString(R.string.error_invalid_email)
                false
            }

            else -> true
        }
    }

    private fun requestReset(email: String) {
        setLoading(true)

        lifecycleScope.launch {
            when (val result = authRepository.requestPasswordReset(email)) {
                is OperationResult.Success -> {
                    setLoading(false)
                    Toast.makeText(
                        this@PasswordResetEmailActivity,
                        result.message,
                        Toast.LENGTH_SHORT
                    ).show()

                    val intent = Intent(
                        this@PasswordResetEmailActivity,
                        PasswordResetCodeActivity::class.java
                    )
                    intent.putExtra(PasswordResetCodeActivity.EXTRA_EMAIL, email)
                    startActivity(intent)
                }

                is OperationResult.Error -> {
                    setLoading(false)
                    Toast.makeText(
                        this@PasswordResetEmailActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnNext.isEnabled = !isLoading && binding.etEmail.text?.isNotEmpty() == true
        binding.etEmail.isEnabled = !isLoading
        binding.tvBack.isEnabled = !isLoading
    }
}


