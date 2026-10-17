package com.example.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO جدول الغرف (Rooms) — الطبقة المحلية (Room/SQLite).
 *
 * يغطي الجدولين `cached_rooms` و `cached_room_participants` في DAO واحد لأن
 * منطق الغرف والمشاركين يُستخدم دائماً معاً (إنشاء غرفة = صف غرفة + صف المضيف).
 *
 * كل الدوال `suspend` أو تُعيد [Flow]، فلا يوجد أي استدعاء يحجب الـ Main Thread.
 * الـ DAO البعيد المكافئ هو `SupabaseRoomsDao` (يخاطب views في Supabase).
 */
@Dao
interface GameRoomDao {

    // =========================================================================
    // الغرف (Rooms)
    // =========================================================================

    /** مراقبة مباشرة لكل الغرف المكاشّة — المصدر الأساسي للواجهة. */
    @Query("SELECT * FROM cached_rooms ORDER BY createdAtMillis DESC")
    fun observeRooms(): Flow<List<GameRoomEntity>>

    @Query("SELECT * FROM cached_rooms ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun getRecentRooms(limit: Int): List<GameRoomEntity>

    @Query("SELECT * FROM cached_rooms WHERE roomCode = :roomCode LIMIT 1")
    suspend fun getRoomByCode(roomCode: String): GameRoomEntity?

    @Query("SELECT COUNT(*) FROM cached_rooms WHERE roomCode = :roomCode")
    suspend fun countRoomsWithCode(roomCode: String): Int

    @Query("SELECT COUNT(*) FROM cached_rooms")
    suspend fun getRoomsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoom(room: GameRoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRooms(rooms: List<GameRoomEntity>)

    @Query("DELETE FROM cached_rooms WHERE roomCode = :roomCode")
    suspend fun deleteRoom(roomCode: String)

    @Query("DELETE FROM cached_rooms")
    suspend fun clearAllRooms()

    /** الغرف التي أُنشئت أوفلاين ولم تُدفع للسيرفر بعد. */
    @Query("SELECT * FROM cached_rooms WHERE isPendingSync = 1 ORDER BY createdAtMillis ASC")
    suspend fun getPendingSyncRooms(): List<GameRoomEntity>

    /** تعليم الغرفة كمتزامنة بعد نجاح الدفع للسيرفر. */
    @Query(
        "UPDATE cached_rooms SET isPendingSync = 0, lastSyncedAtMillis = :syncedAtMillis " +
            "WHERE roomCode = :roomCode"
    )
    suspend fun markRoomSynced(roomCode: String, syncedAtMillis: Long)

    /** تحديث عدد المشاركين المكاشّ بعد أي إضافة/حذف. */
    @Query("UPDATE cached_rooms SET participantCount = :count WHERE roomCode = :roomCode")
    suspend fun updateParticipantCount(roomCode: String, count: Int)

    // =========================================================================
    // المشاركون (Participants)
    // =========================================================================

    /** مراقبة مباشرة لمشاركي غرفة معينة (المضيف أولاً ثم الجاهزية فالنقاط). */
    @Query("SELECT * FROM cached_room_participants WHERE roomCode = :roomCode")
    fun observeParticipants(roomCode: String): Flow<List<RoomParticipantEntity>>

    @Query("SELECT * FROM cached_room_participants WHERE roomCode = :roomCode")
    suspend fun getParticipants(roomCode: String): List<RoomParticipantEntity>

    @Query("SELECT COUNT(*) FROM cached_room_participants WHERE roomCode = :roomCode")
    suspend fun countParticipants(roomCode: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertParticipant(participant: RoomParticipantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertParticipants(participants: List<RoomParticipantEntity>)

    @Query("DELETE FROM cached_room_participants WHERE participantKey = :participantKey")
    suspend fun deleteParticipant(participantKey: String)

    @Query("DELETE FROM cached_room_participants WHERE roomCode = :roomCode")
    suspend fun clearParticipants(roomCode: String)

    @Query("DELETE FROM cached_room_participants")
    suspend fun clearAllParticipants()

    /** المشاركون المضافون أوفلاين والمنتظرون الدفع للسيرفر. */
    @Query("SELECT * FROM cached_room_participants WHERE isPendingSync = 1 ORDER BY joinedAtMillis ASC")
    suspend fun getPendingSyncParticipants(): List<RoomParticipantEntity>

    @Query("UPDATE cached_room_participants SET isPendingSync = 0 WHERE participantKey = :participantKey")
    suspend fun markParticipantSynced(participantKey: String)
}
