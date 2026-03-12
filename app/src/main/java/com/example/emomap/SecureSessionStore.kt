package com.example.emomap

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureSessionStore {

    private const val TAG = "SecureSessionStore"
    private const val PREF_FILE_NAME = "emomap_secure_prefs"
    private const val SESSION_ID_KEY = "session_id"

    @Volatile
    private var encryptedPreferences: SharedPreferences? = null

    fun initialize(context: Context) {
        if (encryptedPreferences != null) return

        synchronized(this) {
            if (encryptedPreferences != null) return

            try {
                val appContext = context.applicationContext
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                encryptedPreferences = EncryptedSharedPreferences.create(
                    appContext,
                    PREF_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize encrypted session storage", e)
                encryptedPreferences = null
            }
        }
    }

    fun saveSessionId(sessionId: String) {
        try {
            encryptedPreferences?.edit()?.putString(SESSION_ID_KEY, sessionId)?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session_id", e)
        }
    }

    fun getSessionId(): String? {
        return try {
            encryptedPreferences?.getString(SESSION_ID_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read session_id", e)
            null
        }
    }

    fun clearSessionId() {
        try {
            encryptedPreferences?.edit()?.remove(SESSION_ID_KEY)?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear session_id", e)
        }
    }
}
