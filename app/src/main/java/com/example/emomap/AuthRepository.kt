package com.example.emomap

import android.content.Context

class AuthRepository(context: Context) {

    init {
        SecureSessionStore.initialize(context.applicationContext)
    }

    private val apiService = NetworkConfig.apiService
    
    suspend fun login(email: String, password: String): AuthResult {
        return try {
            val request = LoginRequest(email, password)
            val response = apiService.login(request)
            
            if (response.isSuccessful) {
                response.body()?.let { sessionResponse ->
                    saveSessionId(sessionResponse.sessionId)
                    AuthResult.Success(sessionResponse.sessionId)
                } ?: AuthResult.Error("Пустой ответ сервера")
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Неверный email или пароль"
                    422 -> "Неверный формат данных"
                    else -> "Ошибка сервера: ${response.code()}"
                }
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            AuthResult.Error("Ошибка сети: ${e.message}")
        }
    }
    
    suspend fun register(email: String, password: String): AuthResult {
        return try {
            val request = RegisterRequest(email, password)
            val response = apiService.register(request)
            
            if (response.isSuccessful) {
                response.body()?.let { sessionResponse ->
                    saveSessionId(sessionResponse.sessionId)
                    AuthResult.Success(sessionResponse.sessionId)
                } ?: AuthResult.Error("Пустой ответ сервера")
            } else {
                val errorMessage = when (response.code()) {
                    400 -> "Пользователь с таким email уже существует"
                    422 -> "Неверный формат данных"
                    else -> "Ошибка сервера: ${response.code()}"
                }
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            AuthResult.Error("Ошибка сети: ${e.message}")
        }
    }
    
    suspend fun logout(): Boolean {
        return try {
            val response = apiService.logout()
            if (response.isSuccessful) {
                clearSessionId()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun saveSessionId(sessionId: String) {
        SecureSessionStore.saveSessionId(sessionId)
    }
    
    fun clearSessionId() {
        SecureSessionStore.clearSessionId()
    }
    
    fun getSessionId(): String? {
        return SecureSessionStore.getSessionId()
    }
    
    fun isLoggedIn(): Boolean {
        return getSessionId() != null
    }
    
    fun logoutSync() {
        clearSessionId()
    }
    
    /**
     * Request password reset code to be sent to the specified email.
     */
    suspend fun requestPasswordReset(email: String): OperationResult {
        return try {
            val request = PasswordResetRequest(email)
            val response = apiService.requestPasswordReset(request)

            if (response.isSuccessful) {
                val message = response.body()?.message ?: "Код для сброса пароля отправлен"
                OperationResult.Success(message)
            } else {
                val errorMessage = when (response.code()) {
                    422 -> "Неверный формат электронной почты"
                    else -> "Ошибка сервера: ${response.code()}"
                }
                OperationResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            OperationResult.Error("Ошибка сети: ${e.message}")
        }
    }

    /**
     * Verify that the reset code is valid for the given email.
     */
    suspend fun verifyPasswordResetCode(email: String, code: String): OperationResult {
        return try {
            val request = PasswordResetVerifyRequest(email = email, code = code)
            val response = apiService.verifyPasswordReset(request)

            if (response.isSuccessful) {
                val isValid = response.body() ?: false
                if (isValid) {
                    OperationResult.Success("Код подтвержден")
                } else {
                    OperationResult.Error("Неверный код или email")
                }
            } else {
                val errorMessage = when (response.code()) {
                    422 -> "Неверный формат данных"
                    else -> "Ошибка сервера: ${response.code()}"
                }
                OperationResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            OperationResult.Error("Ошибка сети: ${e.message}")
        }
    }

    /**
     * Confirm new password using previously verified reset token.
     */
    suspend fun confirmPasswordReset(code: String, newPassword: String): OperationResult {
        return try {
            val request = PasswordResetConfirmRequest(resetToken = code, newPassword = newPassword)
            val response = apiService.confirmPasswordReset(request)

            if (response.isSuccessful) {
                val message = response.body()?.message ?: "Пароль успешно изменен"
                OperationResult.Success(message)
            } else {
                val errorMessage = when (response.code()) {
                    422 -> "Неверный формат данных"
                    else -> "Ошибка сервера: ${response.code()}"
                }
                OperationResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            OperationResult.Error("Ошибка сети: ${e.message}")
        }
    }
}

sealed class AuthResult {
    data class Success(val sessionId: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

sealed class OperationResult {
    data class Success(val message: String) : OperationResult()
    data class Error(val message: String) : OperationResult()
} 
