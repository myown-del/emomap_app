package com.example.emomap

import com.google.gson.annotations.SerializedName

// Authentication Request Models
data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String
)

// Authentication Response Models
data class SessionResponse(
    @SerializedName("session_id")
    val sessionId: String
)

data class UserResponse(
    val id: Int,
    val email: String,
    @SerializedName("registration_date")
    val registrationDate: String,
    val name: String?
)

// Profile Update Model
data class ProfileUpdateRequest(
    val name: String?
)

// Emotion Models
data class EmotionCreate(
    val latitude: Double,
    val longitude: Double,
    val rating: Int,
    val comment: String?
)

data class EmotionResponse(
    val id: Int,
    val latitude: Double,
    val longitude: Double,
    val rating: Int,
    val comment: String?,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("user_id")
    val userId: Int
)

// Error Response Models
data class ValidationError(
    val loc: List<Any>,
    val msg: String,
    val type: String
)

data class HTTPValidationError(
    val detail: List<ValidationError>
)

// Statistics models
data class StatisticPeriod(
    @SerializedName("period_label")
    val periodLabel: String,
    @SerializedName("average_rating")
    val averageRating: Double,
    val count: Int
)

data class EmotionStatisticsResponse(
    @SerializedName("period_type")
    val periodType: String,
    val periods: List<StatisticPeriod>
) 