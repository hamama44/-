package com.example.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_questions")
data class OfflineQuestionEntity(
    @PrimaryKey val id: String,
    val category: String, // "thirty_seconds", "sabaho", "true_false", "pvp", "friendly"
    val question: String,
    val optionsString: String, // Pipe-separated options: "خيار 1|خيار 2|خيار 3|خيار 4"
    val correctIndex: Int,
    val difficulty: String = "medium", // "easy", "medium", "hard"
    val explanation: String = "",
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun getOptionsList(): List<String> {
        return optionsString.split("|").filter { it.isNotBlank() }
    }
}

@Entity(tableName = "offline_pending_sync")
data class OfflinePendingSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameType: String,
    val score: Int,
    val earnedPoints: Int,
    val earnedCoins: Int,
    val playedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(tableName = "party_game_records")
data class PartyLeaderboardRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerName: String,
    val playerAvatar: String,
    val gameType: String, // "أتوبيس كومبليت", "تحدي الـ 5 ثواني", "بدون كلام", "التابو العربي", "كمّل المثل الشعبي", "المليون العربي", "الرابط العجيب", "ديربي التجمعات", "فوازير الذكاء", "لو خيروك", "تحدي الثلاثين", "صباحو تحدي"
    val category: String, // "party" or "football"
    val score: Int,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class CumulativePlayerStat(
    val playerName: String,
    val playerAvatar: String,
    val totalScore: Int,
    val gamesPlayedCount: Int,
    val highestScore: Int,
    val favoriteGame: String,
    val isCurrentUser: Boolean = false
)

data class GameHighScoreStat(
    val gameType: String,
    val category: String,
    val playerName: String,
    val playerAvatar: String,
    val highestScore: Int,
    val details: String,
    val timestamp: Long
)

