package com.example.emomap

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkConfig {
    
    // Backend URL - Updated for Android emulator
    private const val BASE_URL = "http://10.0.2.2:8080/"  // Android emulator to host localhost:8080
    // For real device, use: "http://YOUR_IP_ADDRESS:8080/"
    // For emulator: "http://10.0.2.2:8080/" maps to host's localhost:8080
    
    private var sharedPreferences: SharedPreferences? = null
    
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences("emomap_prefs", Context.MODE_PRIVATE)
    }
    
    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, List<Cookie>>()
        
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
            
            // Save session_id cookie to SharedPreferences
            cookies.find { it.name == "session_id" }?.let { sessionCookie ->
                sharedPreferences?.edit()?.putString("session_id", sessionCookie.value)?.apply()
            }
        }
        
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val savedCookies = cookieStore[url.host] ?: emptyList()
            val sessionId = sharedPreferences?.getString("session_id", null)
            
            // Add session_id cookie if we have one
            return if (sessionId != null) {
                val sessionCookie = Cookie.Builder()
                    .name("session_id")
                    .value(sessionId)
                    .domain(url.host)
                    .build()
                savedCookies + sessionCookie
            } else {
                savedCookies
            }
        }
    }
    
    // Session interceptor to add session_id to headers if cookie is not working
    private val sessionInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val sessionId = sharedPreferences?.getString("session_id", null)
        
        val newRequest = if (sessionId != null) {
            originalRequest.newBuilder()
                .addHeader("Cookie", "session_id=$sessionId")
                .build()
        } else {
            originalRequest
        }
        
        chain.proceed(newRequest)
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(sessionInterceptor)
        .addInterceptor(loggingInterceptor)
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
} 