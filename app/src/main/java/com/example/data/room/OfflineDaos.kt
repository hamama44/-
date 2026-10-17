package com.example.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineQuestionDao {
    @Query("SELECT * FROM offline_questions")
    fun getAllOfflineQuestions(): Flow<List<OfflineQuestionEntity>>

    @Query("SELECT * FROM offline_questions WHERE category = :category")
    fun getQuestionsByCategory(category: String): Flow<List<OfflineQuestionEntity>>

    @Query("SELECT * FROM offline_questions ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestions(limit: Int): List<OfflineQuestionEntity>

    @Query("SELECT * FROM offline_questions WHERE category = :category ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestionsByCategory(category: String, limit: Int): List<OfflineQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<OfflineQuestionEntity>)

    @Query("SELECT COUNT(*) FROM offline_questions")
    suspend fun getQuestionCount(): Int

    @Query("SELECT COUNT(*) FROM offline_questions")
    fun observeQuestionCount(): Flow<Int>
}

@Dao
interface OfflinePendingSyncDao {
    @Query("SELECT * FROM offline_pending_sync WHERE isSynced = 0 ORDER BY playedAt ASC")
    fun getPendingSyncRecords(): Flow<List<OfflinePendingSyncEntity>>

    @Query("SELECT * FROM offline_pending_sync WHERE isSynced = 0 ORDER BY playedAt ASC")
    suspend fun getPendingSyncList(): List<OfflinePendingSyncEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingRecord(record: OfflinePendingSyncEntity)

    @Query("UPDATE offline_pending_sync SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markRecordsAsSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM offline_pending_sync WHERE isSynced = 0")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM offline_pending_sync WHERE isSynced = 1")
    suspend fun clearSyncedRecords()
}

@Dao
interface PartyLeaderboardDao {
    @Query("SELECT * FROM party_game_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<PartyLeaderboardRecordEntity>>

    @Query("SELECT * FROM party_game_records WHERE category = :category ORDER BY timestamp DESC")
    fun getRecordsByCategory(category: String): Flow<List<PartyLeaderboardRecordEntity>>

    @Query("SELECT * FROM party_game_records WHERE gameType = :gameType ORDER BY score DESC LIMIT :limit")
    fun getTopScoresForGame(gameType: String, limit: Int = 10): Flow<List<PartyLeaderboardRecordEntity>>

    @Query("SELECT * FROM party_game_records ORDER BY score DESC")
    suspend fun getAllRecordsSnapshot(): List<PartyLeaderboardRecordEntity>

    @Query("SELECT COUNT(*) FROM party_game_records")
    suspend fun getRecordCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: PartyLeaderboardRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<PartyLeaderboardRecordEntity>)

    @Query("DELETE FROM party_game_records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM party_game_records")
    suspend fun clearAllRecords()
}

