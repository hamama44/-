package com.example.data.supabase

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseQuestion(
    @Json(name = "id") val id: String,
    @Json(name = "game_type") val gameType: String,
    @Json(name = "question") val question: String,
    @Json(name = "options") val options: List<String> = emptyList(),
    @Json(name = "correct_index") val correctIndex: Int = 0,
    @Json(name = "difficulty") val difficulty: String? = "medium",
    @Json(name = "category") val category: String? = "عامة",
    @Json(name = "explanation") val explanation: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabasePlayer(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "nationality") val nationality: String? = null,
    @Json(name = "current_club") val currentClub: String? = null,
    @Json(name = "position") val position: String? = null,
    @Json(name = "shirt_number") val shirtNumber: Int? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseScore(
    @Json(name = "id") val id: String? = null,
    @Json(name = "player_name") val playerName: String,
    @Json(name = "points") val points: Int,
    @Json(name = "coins") val coins: Int,
    @Json(name = "level_title") val levelTitle: String? = "مشجع هاوٍ",
    @Json(name = "favorite_club") val favoriteClub: String? = "ريال مدريد"
)
