package com.example.emomap

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {
    
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<SessionResponse>
    
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<SessionResponse>
    
    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    // Password reset endpoints
    @POST("api/auth/password-reset/request")
    suspend fun requestPasswordReset(
        @Body request: PasswordResetRequest
    ): Response<PasswordResetRequestResponse>

    @POST("api/auth/password-reset/verify")
    suspend fun verifyPasswordReset(
        @Body request: PasswordResetVerifyRequest
    ): Response<Boolean>

    @POST("api/auth/password-reset/confirm")
    suspend fun confirmPasswordReset(
        @Body request: PasswordResetConfirmRequest
    ): Response<PasswordResetRequestResponse>
    
    @POST("api/emotions/")
    suspend fun createEmotion(@Body request: EmotionCreate): Response<EmotionResponse>
    
    @GET("api/emotions/me")
    suspend fun getUserEmotions(): Response<List<EmotionResponse>>
    
    @GET("api/users/me/profile")
    suspend fun getCurrentUser(): Response<UserResponse>
    
    @PUT("api/users/me/profile")
    suspend fun updateProfile(@Body request: ProfileUpdateRequest): Response<UserResponse>
    
    @GET("api/emotions/csv-export")
    suspend fun exportEmotionsCSV(): Response<ResponseBody>
    
    @GET("api/emotions/statistics/average/{period_type}")
    suspend fun getEmotionStatistics(
        @Path("period_type") periodType: String
    ): Response<EmotionStatisticsResponse>
} 