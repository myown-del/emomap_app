package com.example.emomap

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.emomap.databinding.ActivityPasswordResetCodeBinding
import kotlinx.coroutines.launch

/**
 * Step 2 of password reset: verify 4-digit code sent to email.
 */
class PasswordResetCodeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordResetCodeBinding
    private lateinit var authRepository: AuthRepository

    private lateinit var email: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPasswordResetCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = AuthRepository(this)
        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()

        setupListeners()
    }

    private fun setupListeners() {
        binding.tvBack.setOnClickListener {
            finish()
        }

        binding.etCode.addTextChangedListener(SimpleTextWatcher {
            val code = binding.etCode.text?.toString().orEmpty()
            binding.btnNext.isEnabled = code.length == 4
        })

        binding.btnNext.setOnClickListener {
            val code = binding.etCode.text?.toString().orEmpty()
            if (code.length != 4) return@setOnClickListener
            verifyCode(code)
        }
    }

    private fun verifyCode(code: String) {
        setLoading(true)

        lifecycleScope.launch {
            when (val result = authRepository.verifyPasswordResetCode(email, code)) {
                is OperationResult.Success -> {
                    setLoading(false)
                    val intent = Intent(
                        this@PasswordResetCodeActivity,
                        PasswordResetNewPasswordActivity::class.java
                    )
                    intent.putExtra(PasswordResetNewPasswordActivity.EXTRA_CODE, code)
                    startActivity(intent)
                }

                is OperationResult.Error -> {
                    setLoading(false)
                    Toast.makeText(
                        this@PasswordResetCodeActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnNext.isEnabled = !isLoading && binding.etCode.text?.length == 4
        binding.etCode.isEnabled = !isLoading
        binding.tvBack.isEnabled = !isLoading
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }
}


