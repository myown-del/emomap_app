package com.example.emomap

import android.content.Context
import android.content.SharedPreferences
import retrofit2.Response

class AuthRepository(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("emomap_prefs", Context.MODE_PRIVATE)
    
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
        sharedPreferences.edit()
            .putString("session_id", sessionId)
            .apply()
    }
    
    fun clearSessionId() {
        sharedPreferences.edit()
            .remove("session_id")
            .apply()
    }
    
    fun getSessionId(): String? {
        return sharedPreferences.getString("session_id", null)
    }
    
    fun isLoggedIn(): Boolean {
        return getSessionId() != null
    }
    
    fun logoutSync() {
        clearSessionId()
    }
}

sealed class AuthResult {
    data class Success(val sessionId: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
} 