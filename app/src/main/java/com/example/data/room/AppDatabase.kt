package com.example.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        OfflineQuestionEntity::class,
        OfflinePendingSyncEntity::class,
        PartyLeaderboardRecordEntity::class,
        GameRoomEntity::class,
        RoomParticipantEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun offlineQuestionDao(): OfflineQuestionDao
    abstract fun offlinePendingSyncDao(): OfflinePendingSyncDao
    abstract fun partyLeaderboardDao(): PartyLeaderboardDao

    /** DAO جدول الغرف (Rooms) والمشاركين — الكاش المحلي لطبقة الأونلاين. */
    abstract fun gameRoomDao(): GameRoomDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * ترقية 2 → 3: إضافة جداول كاش الغرف والمشاركين فقط.
         *
         * مكتوبة يدوياً (بدل الاعتماد على fallbackToDestructiveMigration) حتى لا
         * نفقد سجلات اللاعبين المحفوظة في `party_game_records` والأسئلة المكاشّة
         * عند تحديث التطبيق. الأعمدة مطابقة تماماً لما يولّده Room من الكيانات
         * (بدون DEFAULT لأن قيم Kotlin الافتراضية ليست قيم أعمدة في المخطط).
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_rooms` (" +
                        "`roomCode` TEXT NOT NULL, " +
                        "`hostName` TEXT NOT NULL, " +
                        "`remoteId` TEXT, " +
                        "`gameType` TEXT NOT NULL, " +
                        "`statusWire` TEXT NOT NULL, " +
                        "`currentRound` INTEGER NOT NULL, " +
                        "`maxRounds` INTEGER NOT NULL, " +
                        "`maxPlayers` INTEGER NOT NULL, " +
                        "`isPrivate` INTEGER NOT NULL, " +
                        "`region` TEXT NOT NULL, " +
                        "`participantCount` INTEGER NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, " +
                        "`lastSyncedAtMillis` INTEGER NOT NULL, " +
                        "`isPendingSync` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`roomCode`)" +
                        ")"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_room_participants` (" +
                        "`participantKey` TEXT NOT NULL, " +
                        "`roomCode` TEXT NOT NULL, " +
                        "`playerName` TEXT NOT NULL, " +
                        "`remoteId` TEXT, " +
                        "`avatarEmoji` TEXT NOT NULL, " +
                        "`teamColor` TEXT NOT NULL, " +
                        "`score` INTEGER NOT NULL, " +
                        "`isReady` INTEGER NOT NULL, " +
                        "`isHost` INTEGER NOT NULL, " +
                        "`joinedAtMillis` INTEGER NOT NULL, " +
                        "`isPendingSync` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`participantKey`)" +
                        ")"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_room_participants_roomCode` " +
                        "ON `cached_room_participants` (`roomCode`)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "football_challenge_offline.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
