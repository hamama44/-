package com.example.data.room

import android.content.Context
import com.example.data.FootballRepository
import com.example.data.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OfflineSyncResult(
    val syncedCount: Int,
    val totalPoints: Int,
    val totalCoins: Int,
    val message: String
)

class OfflineRepository(
    context: Context,
    private val footballRepository: FootballRepository
) {
    private val database = AppDatabase.getDatabase(context)
    val questionDao = database.offlineQuestionDao()
    val pendingSyncDao = database.offlinePendingSyncDao()
    val partyLeaderboardDao = database.partyLeaderboardDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Manual/System toggle for Offline Mode
    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    // Syncing state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Sync status message / toast
    private val _lastSyncResult = MutableStateFlow<OfflineSyncResult?>(null)
    val lastSyncResult: StateFlow<OfflineSyncResult?> = _lastSyncResult.asStateFlow()

    val pendingSyncCount: StateFlow<Int> = pendingSyncDao.observePendingCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    val cachedQuestionCount: StateFlow<Int> = questionDao.observeQuestionCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        scope.launch {
            seedOfflineQuestionsIfEmpty()
            seedPartyLeaderboardIfEmpty()
        }
    }

    fun setOfflineMode(enabled: Boolean) {
        _isOfflineMode.value = enabled
    }

    fun toggleOfflineMode() {
        _isOfflineMode.value = !_isOfflineMode.value
    }

    fun dismissLastSyncResult() {
        _lastSyncResult.value = null
    }

    suspend fun seedOfflineQuestionsIfEmpty() = withContext(Dispatchers.IO) {
        val count = questionDao.getQuestionCount()
        if (count > 0) return@withContext

        val entities = mutableListOf<OfflineQuestionEntity>()

        // 1. Thirty Seconds Questions
        footballRepository.getThirtySecondsQuestions().forEach { q ->
            entities.add(
                OfflineQuestionEntity(
                    id = "offline_${q.id}",
                    category = "thirty_seconds",
                    question = q.question,
                    optionsString = q.options.joinToString("|"),
                    correctIndex = q.correctIndex,
                    difficulty = "medium",
                    explanation = "الفئة: ${q.category}"
                )
            )
        }

        // 2. Sabaho Questions
        footballRepository.getSabahoQuestions().forEach { q ->
            entities.add(
                OfflineQuestionEntity(
                    id = "offline_${q.id}",
                    category = "sabaho",
                    question = q.question,
                    optionsString = q.options.joinToString("|"),
                    correctIndex = q.correctIndex,
                    difficulty = when (q.difficulty) {
                        com.example.data.Difficulty.EASY -> "easy"
                        com.example.data.Difficulty.MEDIUM -> "medium"
                        com.example.data.Difficulty.HARD -> "hard"
                    },
                    explanation = q.explanation
                )
            )
        }

        // 3. True or False Questions
        footballRepository.getTrueFalseQuestions().forEach { q ->
            entities.add(
                OfflineQuestionEntity(
                    id = "offline_${q.id}",
                    category = "true_false",
                    question = q.statement,
                    optionsString = "صح|خطأ",
                    correctIndex = if (q.isTrue) 0 else 1,
                    difficulty = "medium",
                    explanation = q.explanation
                )
            )
        }

        // 4. PvP / Friendly match questions
        footballRepository.getPvPQuestions().forEach { q ->
            entities.add(
                OfflineQuestionEntity(
                    id = "offline_${q.id}",
                    category = "pvp_friendly",
                    question = q.question,
                    optionsString = q.options.joinToString("|"),
                    correctIndex = q.correctIndex,
                    difficulty = "medium",
                    explanation = "سؤال مواجهة كرة القدم الودية"
                )
            )
        }

        questionDao.insertQuestions(entities)
    }

    suspend fun recordOfflineGamePlayed(
        gameType: String,
        score: Int,
        earnedPoints: Int,
        earnedCoins: Int
    ) = withContext(Dispatchers.IO) {
        val record = OfflinePendingSyncEntity(
            gameType = gameType,
            score = score,
            earnedPoints = earnedPoints,
            earnedCoins = earnedCoins
        )
        pendingSyncDao.insertPendingRecord(record)
    }

    suspend fun syncPendingRecords(): OfflineSyncResult = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            val pendingList = pendingSyncDao.getPendingSyncList()
            if (pendingList.isEmpty()) {
                val res = OfflineSyncResult(
                    syncedCount = 0,
                    totalPoints = 0,
                    totalCoins = 0,
                    message = "البيانات متزامنة بالكامل! لا توجد سجلات أوفلاين معلقة."
                )
                _lastSyncResult.value = res
                return@withContext res
            }

            var totalPoints = 0
            var totalCoins = 0
            val ids = mutableListOf<Long>()

            pendingList.forEach { record ->
                totalPoints += record.earnedPoints
                totalCoins += record.earnedCoins
                ids.add(record.id)
            }

            // Sync with profile
            withContext(Dispatchers.Main) {
                footballRepository.addPointsAndCoins(
                    earnedPoints = totalPoints,
                    earnedCoins = totalCoins,
                    gameTitle = "مزامنة أوفلاين (${pendingList.size} مباريات)"
                )
            }

            // Mark as synced in Room
            pendingSyncDao.markRecordsAsSynced(ids)

            val result = OfflineSyncResult(
                syncedCount = pendingList.size,
                totalPoints = totalPoints,
                totalCoins = totalCoins,
                message = "تمت مزامنة ${pendingList.size} مباريات أوفلاين بنجاح! +$totalPoints نقطة و +$totalCoins عملة ⭐"
            )
            _lastSyncResult.value = result
            result
        } finally {
            _isSyncing.value = false
        }
    }

    // =========================================================================
    // ROOM PERSISTENCE: PARTY GAMES LEADERBOARD & RECORDS
    // =========================================================================

    fun observeAllPartyRecords(): Flow<List<PartyLeaderboardRecordEntity>> {
        return partyLeaderboardDao.getAllRecords()
    }

    fun observeCumulativeLeaderboard(categoryFilter: String = "all"): Flow<List<CumulativePlayerStat>> {
        val sourceFlow = if (categoryFilter == "all") {
            partyLeaderboardDao.getAllRecords()
        } else {
            partyLeaderboardDao.getRecordsByCategory(categoryFilter)
        }

        return sourceFlow.map { records ->
            val currentUser = footballRepository.userProfile.value
            val grouped = records.groupBy { it.playerName }

            grouped.map { (name, playerRecords) ->
                val total = playerRecords.sumOf { it.score }
                val count = playerRecords.size
                val maxScore = playerRecords.maxOfOrNull { it.score } ?: 0
                val favGame = playerRecords.groupBy { it.gameType }
                    .maxByOrNull { it.value.size }?.key ?: "ألعاب الصالون"
                val avatar = playerRecords.firstOrNull()?.playerAvatar ?: "👤"
                val isCurrent = name.trim().equals(currentUser.playerName.trim(), ignoreCase = true)

                CumulativePlayerStat(
                    playerName = name,
                    playerAvatar = avatar,
                    totalScore = total,
                    gamesPlayedCount = count,
                    highestScore = maxScore,
                    favoriteGame = favGame,
                    isCurrentUser = isCurrent
                )
            }.sortedByDescending { it.totalScore }
        }
    }

    fun observeGameHighScores(): Flow<List<GameHighScoreStat>> {
        return partyLeaderboardDao.getAllRecords().map { records ->
            val groupedByGame = records.groupBy { it.gameType }
            groupedByGame.map { (game, gameRecords) ->
                val topRecord = gameRecords.maxByOrNull { it.score } ?: gameRecords.first()
                GameHighScoreStat(
                    gameType = game,
                    category = topRecord.category,
                    playerName = topRecord.playerName,
                    playerAvatar = topRecord.playerAvatar,
                    highestScore = topRecord.score,
                    details = topRecord.details,
                    timestamp = topRecord.timestamp
                )
            }.sortedByDescending { it.highestScore }
        }
    }

    suspend fun recordPartyGameScore(
        playerName: String,
        playerAvatar: String,
        gameType: String,
        category: String,
        score: Int,
        details: String = ""
    ) = withContext(Dispatchers.IO) {
        val entity = PartyLeaderboardRecordEntity(
            playerName = playerName.ifBlank { "لاعب التحدي" },
            playerAvatar = playerAvatar.ifBlank { "👤" },
            gameType = gameType,
            category = category,
            score = score,
            details = details,
            timestamp = System.currentTimeMillis()
        )
        partyLeaderboardDao.insertRecord(entity)
    }

    suspend fun clearAllPartyRecords() = withContext(Dispatchers.IO) {
        partyLeaderboardDao.clearAllRecords()
    }

    suspend fun resetPartyLeaderboardWithPresets() = withContext(Dispatchers.IO) {
        partyLeaderboardDao.clearAllRecords()
        seedPartyLeaderboardIfEmpty()
    }

    suspend fun seedPartyLeaderboardIfEmpty() = withContext(Dispatchers.IO) {
        val count = partyLeaderboardDao.getRecordCount()
        if (count > 0) return@withContext

        val currentUser = footballRepository.userProfile.value
        val now = System.currentTimeMillis()

        val sampleRecords = listOf(
            // Current User Initial Record
            PartyLeaderboardRecordEntity(
                playerName = currentUser.playerName,
                playerAvatar = currentUser.avatarEmoji,
                gameType = "أتوبيس كومبليت",
                category = "party",
                score = 110,
                details = "حرف الميم • إجابات كاملة 🚌",
                timestamp = now - 3600000
            ),
            PartyLeaderboardRecordEntity(
                playerName = currentUser.playerName,
                playerAvatar = currentUser.avatarEmoji,
                gameType = "تحدي الـ 5 ثواني",
                category = "party",
                score = 80,
                details = "سرعة بديهة خارقة ⚡",
                timestamp = now - 7200000
            ),
            PartyLeaderboardRecordEntity(
                playerName = currentUser.playerName,
                playerAvatar = currentUser.avatarEmoji,
                gameType = "كمّل المثل الشعبي",
                category = "party",
                score = 90,
                details = "سلسلة 6 أمثال متتالية 📜",
                timestamp = now - 14400000
            ),

            // Top Champions across party & gathering games
            PartyLeaderboardRecordEntity(
                playerName = "كابتن سري",
                playerAvatar = "👑",
                gameType = "المليون العربي",
                category = "party",
                score = 125000,
                details = "وصل للمستوى 12 ببراعة 💰",
                timestamp = now - 1800000
            ),
            PartyLeaderboardRecordEntity(
                playerName = "كابتن سري",
                playerAvatar = "👑",
                gameType = "أتوبيس كومبليت",
                category = "party",
                score = 130,
                details = "8 فئات مكتملة في 40 ثانية",
                timestamp = now - 86400000
            ),
            PartyLeaderboardRecordEntity(
                playerName = "كابتن سري",
                playerAvatar = "👑",
                gameType = "تحدي الثلاثين",
                category = "football",
                score = 180,
                details = "إجابات كروية شاملة ⚽",
                timestamp = now - 172800000
            ),

            PartyLeaderboardRecordEntity(
                playerName = "عمر المدريدي",
                playerAvatar = "⚪",
                gameType = "التابو العربي",
                category = "party",
                score = 95,
                details = "بدون أي مخالفة محظورة 🚫",
                timestamp = now - 43200000
            ),
            PartyLeaderboardRecordEntity(
                playerName = "عمر المدريدي",
                playerAvatar = "⚪",
                gameType = "بدون كلام",
                category = "party",
                score = 120,
                details = "تخمين 6 أفلام ومسرحيات 🎭",
                timestamp = now - 129600000
            ),
            PartyLeaderboardRecordEntity(
                playerName = "عمر المدريدي",
                playerAvatar = "⚪",
                gameType = "صباحو تحدي",
                category = "football",
                score = 140,
                details = "ملك الكلاسيكو والديربيات ⚽",
                timestamp = now - 259200000
            ),

            PartyLeaderboardRecordEntity(
                playerName = "سارة الذكية",
                playerAvatar = "🌟",
                gameType = "كمّل المثل الشعبي",
                category = "party",
                score = 160,
                details = "سلسلة 10 أمثال شعبية متتالية 📜",
                timestamp = now - 21600000
            ),
            PartyLeaderboardRecordEntity(
                playerName = "سارة الذكية",
                playerAvatar = "🌟",
                gameType = "الرابط العجيب",
                category = "party",
                score = 140,
                details = "حل 7 روابط متتالية 🔍",
                timestamp = now - 64800000
            ),
            PartyLeaderboardRecordEntity(
                playerName = "سارة الذكية",
                playerAvatar = "🌟",
                gameType = "فوازير الذكاء",
                category = "party",
                score = 110,
                details = "ألغاز شعرية وتراثية 💡",
                timestamp = now - 108000000
            ),

            PartyLeaderboardRecordEntity(
                playerName = "أحمد فخر العرب",
                playerAvatar = "🇪🇬",
                gameType = "ديربي التجمعات",
                category = "party",
                score = 150,
                details = "قيادة الفريق الأحمر للفوز 🔴",
                timestamp = now - 32400000
            ),
            PartyLeaderboardRecordEntity(
                playerName = "أحمد فخر العرب",
                playerAvatar = "🇪🇬",
                gameType = "تحدي الـ 5 ثواني",
                category = "party",
                score = 100,
                details = "سرعة بديهة قياسية ⚡",
                timestamp = now - 75600000
            ),

            PartyLeaderboardRecordEntity(
                playerName = "نور الكلمات",
                playerAvatar = "📚",
                gameType = "أتوبيس كومبليت",
                category = "party",
                score = 145,
                details = "قاموس لغوي فوري 🚌",
                timestamp = now - 54000000
            ),
            PartyLeaderboardRecordEntity(
                playerName = "نور الكلمات",
                playerAvatar = "📚",
                gameType = "الرابط العجيب",
                category = "party",
                score = 120,
                details = "حل روابط العلوم والأدب 🔍",
                timestamp = now - 97200000
            )
        )

        partyLeaderboardDao.insertRecords(sampleRecords)
    }
}

