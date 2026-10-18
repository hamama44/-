package com.example.data.room

import android.content.Context
import com.example.data.questions.CloudQuestionDao
import com.example.data.questions.CloudQuestionEntity
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
        RoomParticipantEntity::class,
        CloudQuestionEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun offlineQuestionDao(): OfflineQuestionDao
    abstract fun offlinePendingSyncDao(): OfflinePendingSyncDao
    abstract fun partyLeaderboardDao(): PartyLeaderboardDao

    /** DAO جدول الغرف (Rooms) والمشاركين — الكاش المحلي لطبقة الأونلاين. */
    abstract fun gameRoomDao(): GameRoomDao

    /** DAO كاش الأسئلة السحابية التي ينشرها البوت. */
    abstract fun cloudQuestionDao(): CloudQuestionDao

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

        /**
         * ترقية 3 → 4: جدول كاش الأسئلة السحابية اللي بينشرها البوت.
         *
         * مكتوبة يدوياً مثل MIGRATION_2_3 وبنفس القيد: **بدون أي DEFAULT clause**
         * لأن مُحقّق مخطط Room بيرفض قيمة افتراضية في العمود مش معلنة بـ
         * `@ColumnInfo(defaultValue = ...)` في الكيان. الكيان مفيهوش قيم افتراضية
         * أصلاً، فكل القيم بتيجي من المُدرِج ⇒ المخطط مطابق حرفياً.
         *
         * `contentHash` عليه يونيكون: نفس المحتوى من تشغيلين للبوت ما يتخزنش
         * مرتين في الكاش، حتى لو السيرفر رجّعه (لازم يفضله safe).
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cloud_questions` (" +
                        "`id` TEXT NOT NULL, " +
                        "`gameType` TEXT NOT NULL, " +
                        "`question` TEXT NOT NULL, " +
                        "`optionsString` TEXT NOT NULL, " +
                        "`correctIndex` INTEGER NOT NULL, " +
                        "`difficulty` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`explanation` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`contentHash` TEXT NOT NULL, " +
                        "`publishedAtMillis` INTEGER NOT NULL, " +
                        "`cachedAtMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`)" +
                        ")"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cloud_questions_gameType` " +
                        "ON `cloud_questions` (`gameType`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_cloud_questions_contentHash` " +
                        "ON `cloud_questions` (`contentHash`)"
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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
