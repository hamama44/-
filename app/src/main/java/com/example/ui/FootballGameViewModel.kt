package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.StadiumAudioEffects
import com.example.data.*
import com.example.data.lan.DiscoveredHost
import com.example.data.lan.LanGameManager
import com.example.data.lan.LanProtocol
import com.example.data.room.CumulativePlayerStat
import com.example.data.room.GameHighScoreStat
import com.example.data.room.GameRoom
import com.example.data.room.GameRoomRepository
import com.example.data.room.OfflineRepository
import com.example.data.room.OfflineSyncResult
import com.example.data.room.PartyLeaderboardRecordEntity
import com.example.data.room.RoomParticipant
import com.example.data.room.RoomTeamScores
import com.example.data.room.failureMessageOrNull
import com.example.data.room.getOrNull
import com.example.data.supabase.RealtimeEvent
import com.example.data.supabase.SupabaseClientProvider
import com.example.data.supabase.SupabaseRealtimeClient
import com.example.data.supabase.SupabaseScore
import com.example.data.supabase.SupabaseUser
import com.example.data.supabase.addNewPlayer
import com.example.data.questions.QuestionFeedLogic
import com.example.data.questions.QuestionFeedRepository
import com.example.data.daily.DailyChallenge
import com.example.data.daily.DailyChallengeManager
import com.example.data.ai.GeminiSpeechAuditor
import com.example.data.ai.AiSpeechEvaluation
import com.example.data.ai.AiTabooEvaluation
import com.example.data.ai.AiSpokenAnswerEvaluation
import com.example.data.voice.VoicePermissionRepository
import com.example.data.voice.VoicePermissionStatus
import com.example.data.voice.SpeechToTextManager
import com.example.data.voice.SpeechState
import com.example.notifications.StadiumNotificationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class GameScreen {
    object Home : GameScreen()
    object ThirtySecondsSelect : GameScreen()
    object ThirtySecondsQuiz : GameScreen()
    object ThirtySecondsPlayers : GameScreen()
    object Sabaho : GameScreen()
    object MysteryPlayerScreen : GameScreen()
    object CareerPathScreen : GameScreen()
    object TrueFalseScreen : GameScreen()
    object EventOrderingScreen : GameScreen()
    object LeaderboardScreen : GameScreen()
    object ProfileScreen : GameScreen()
    object PvPMatchScreen : GameScreen()
    object FriendInviteScreen : GameScreen()
    object AutobusCompleteScreen : GameScreen()
    object FiveSecondsScreen : GameScreen()
    object WouldYouRatherScreen : GameScreen()
    object GeneralRiddlesScreen : GameScreen()
    object BedoonKalamScreen : GameScreen()
    object TabooScreen : GameScreen()
    object ProverbsScreen : GameScreen()
    object MillionaireScreen : GameScreen()
    object FourCluesScreen : GameScreen()
    object PassAndPlayScreen : GameScreen()
    object FootballTicTacToeScreen : GameScreen()
    object FootballAuctionScreen : GameScreen()
    object MissingXIScreen : GameScreen()
    object OddOneOutScreen : GameScreen()
    object JerseyAndTransfersScreen : GameScreen()
    object SpyGameScreen : GameScreen()
    object TruthOrDareScreen : GameScreen()
    object ThirtySecondsRoundsScreen : GameScreen()
    object SupabaseOnlineScreen : GameScreen()

    /** غرفة المباراة المباشرة: مشاركون لحظيون + نتائج الفرق + تحكم المضيف. */
    object RoomMatch : GameScreen()

    /**
     * ردهة الهوتسبوت/الشبكة المحلية: تشغيل سيرفر، بحث تلقائي عن سيرفرات،
     * أو اتصال يدوي بعنوان IP.
     */
    object HotspotLobby : GameScreen()
}

class FootballGameViewModel(application: Application) : AndroidViewModel(application) {
    val repository = FootballRepository(application)
    val audioEffects = StadiumAudioEffects(application)
    val offlineRepository = OfflineRepository(application, repository)
    val dailyChallengeManager = DailyChallengeManager(application)
    val voicePermissionRepository = VoicePermissionRepository(application)
    val speechToTextManager = SpeechToTextManager(application)

    /** مستودع الغرف الأونلاين: DAO محلي (Room) + DAO بعيد (Supabase). */
    val gameRoomRepository = GameRoomRepository.get(application)

    private val _dailyChallenge = MutableStateFlow(dailyChallengeManager.getTodayChallenge())
    val dailyChallenge: StateFlow<DailyChallenge> = _dailyChallenge.asStateFlow()

    private val _isDailyReminderEnabled = MutableStateFlow(dailyChallengeManager.isReminderEnabled())
    val isDailyReminderEnabled: StateFlow<Boolean> = _isDailyReminderEnabled.asStateFlow()

    // -------------------------------------------------------------------------
    // MICROPHONE PERMISSIONS & SPEECH-TO-TEXT ENGINE
    // -------------------------------------------------------------------------
    val voicePermissionStatus: StateFlow<VoicePermissionStatus> = voicePermissionRepository.permissionStatus
    val isVoiceGamingEnabled: StateFlow<Boolean> = voicePermissionRepository.isVoiceGamingEnabled
    val speechState: StateFlow<SpeechState> = speechToTextManager.speechState
    val livePartialSpokenText: StateFlow<String> = speechToTextManager.partialText
    val liveSoundLevel: StateFlow<Float> = speechToTextManager.soundLevel

    fun onMicPermissionResult(granted: Boolean) {
        voicePermissionRepository.onPermissionResult(granted)
    }

    fun startListeningForVoice(
        language: String = "ar-SA",
        prompt: String = "تحدث الآن في ملعب التحدي 🎙️⚽"
    ) {
        speechToTextManager.startListening(language, prompt)
    }

    fun stopListeningForVoice() {
        speechToTextManager.stopListening()
    }

    fun cancelListeningForVoice() {
        speechToTextManager.cancelListening()
    }

    // -------------------------------------------------------------------------
    // AI SPEECH & ANSWER AUDITOR (تدقيق الذكاء الاصطناعي والكلام)
    // -------------------------------------------------------------------------
    private val _aiIsAuditing = MutableStateFlow(false)
    val aiIsAuditing: StateFlow<Boolean> = _aiIsAuditing.asStateFlow()

    private val _aiAuditFeedback = MutableStateFlow<String?>(null)
    val aiAuditFeedback: StateFlow<String?> = _aiAuditFeedback.asStateFlow()

    private val _aiAuditedItems = MutableStateFlow<List<String>>(emptyList())
    val aiAuditedItems: StateFlow<List<String>> = _aiAuditedItems.asStateFlow()

    private val _lastSpokenAnswerEvaluation = MutableStateFlow<AiSpokenAnswerEvaluation?>(null)
    val lastSpokenAnswerEvaluation: StateFlow<AiSpokenAnswerEvaluation?> = _lastSpokenAnswerEvaluation.asStateFlow()

    fun clearAiFeedback() {
        _aiAuditFeedback.value = null
        _aiAuditedItems.value = emptyList()
        _lastSpokenAnswerEvaluation.value = null
    }

    fun clearSpokenAnswerEvaluation() {
        _lastSpokenAnswerEvaluation.value = null
    }

    fun refreshDailyChallenge() {
        _dailyChallenge.value = dailyChallengeManager.getTodayChallenge()
    }

    fun rerollDailyChallenge() {
        audioEffects.playClick()
        _dailyChallenge.value = dailyChallengeManager.rerollChallenge()
    }

    fun claimDailyChallengeReward() {
        val reward = dailyChallengeManager.claimReward()
        if (reward != null) {
            audioEffects.playVictory()
            repository.addPointsAndCoins(
                earnedPoints = reward.first,
                earnedCoins = reward.second,
                gameTitle = "مكافأة إتمام تحدي اليوم 🎯"
            )
            _dailyChallenge.value = dailyChallengeManager.getTodayChallenge()
        }
    }

    fun toggleDailyReminder(enabled: Boolean) {
        audioEffects.playClick()
        _isDailyReminderEnabled.value = enabled
        dailyChallengeManager.setReminderEnabled(enabled)
    }

    fun sendTestDailyNotification() {
        audioEffects.playClick()
        val ch = _dailyChallenge.value
        StadiumNotificationManager(getApplication()).showDailyChallengeReminderNow(
            challengeTitle = ch.title,
            gameName = ch.gameTitle,
            targetDesc = ch.goalDescription
        )
    }

    fun launchChallengeGame(challenge: DailyChallenge) {
        audioEffects.playClick()
        when (challenge.gameTypeTag) {
            "autobus" -> startAutobusGame()
            "five_seconds" -> startFiveSecondsGame()
            "taboo" -> startTabooGame()
            "bedoon_kalam" -> startBedoonKalam()
            "proverbs" -> startProverbsGame()
            "millionaire" -> startMillionaireGame()
            "four_clues" -> startFourCluesGame()
            "sabaho" -> startSabahoChallenge()
            "thirty_sec" -> startThirtySecondsQuiz()
            "tictactoe" -> navigateTo(GameScreen.FootballTicTacToeScreen)
            "auction" -> navigateTo(GameScreen.FootballAuctionScreen)
            "missing_xi" -> navigateTo(GameScreen.MissingXIScreen)
            "odd_one_out" -> navigateTo(GameScreen.OddOneOutScreen)
            "jersey_transfers" -> navigateTo(GameScreen.JerseyAndTransfersScreen)
            "spy" -> navigateTo(GameScreen.SpyGameScreen)
            "truth_or_dare" -> navigateTo(GameScreen.TruthOrDareScreen)
            "nasouh_rounds" -> navigateTo(GameScreen.ThirtySecondsRoundsScreen)
            else -> navigateTo(GameScreen.Home)
        }
    }

    fun auditFiveSecondsSpeech(spokenText: String) {
        if (spokenText.isBlank()) return
        viewModelScope.launch {
            _aiIsAuditing.value = true
            audioEffects.playTick()
            val prompt = _fiveSecondsPromptState.value.prompt
            val result = GeminiSpeechAuditor.auditFiveSecondsChallenge(prompt, spokenText)
            _aiIsAuditing.value = false
            _aiAuditFeedback.value = result.feedback
            _aiAuditedItems.value = result.detectedItems

            if (result.isValid) {
                _fiveSecondsCount.value = 3
                _fiveSecondsIsRunning.value = false
                fiveSecondsTimerJob?.cancel()
                _fiveSecondsRoundFinished.value = true
                _fiveSecondsSuccess.value = true
                _fiveSecondsScore.value += 1
                audioEffects.playVictory()
                repository.addPointsAndCoins(50, 20)
                recordPartyGameScore(
                    "تحدي الـ 5 ثواني",
                    "party",
                    50,
                    "إجابة صوتية دقيقة بالمايك والذكاء الاصطناعي (${result.detectedItems.joinToString("، ")}) 🎙️🤖"
                )
            } else {
                audioEffects.playWrong()
            }
        }
    }

    fun auditTabooSpeech(spokenText: String) {
        if (spokenText.isBlank()) return
        val currentCard = _tabooCurrent.value
        viewModelScope.launch {
            _aiIsAuditing.value = true
            audioEffects.playTick()
            val result = GeminiSpeechAuditor.auditTabooSpeech(
                mainWord = currentCard.mainWord,
                forbiddenWords = currentCard.forbiddenWords,
                spokenDescription = spokenText
            )
            _aiIsAuditing.value = false
            _aiAuditFeedback.value = result.feedback

            if (result.isViolation) {
                audioEffects.playWrong()
                _tabooPenalties.value += 1
            } else if (result.isSmartDescription) {
                audioEffects.playVictory()
                _tabooScore.value += 1
                repository.addPointsAndCoins(25, 10)
                recordPartyGameScore(
                    "التابو العربي",
                    "party",
                    25,
                    "شرح صوتي خالٍ من المحظورات (${currentCard.mainWord}) تدقيق الذكاء الاصطناعي 🎙️🤖"
                )
            }
        }
    }

    fun auditAutobusVoiceInput(type: AutobusFieldType, spokenText: String) {
        if (spokenText.isBlank()) return
        val letter = _autobusLetter.value.letter
        viewModelScope.launch {
            _aiIsAuditing.value = true
            audioEffects.playTick()
            val result = GeminiSpeechAuditor.auditAutobusVoice(letter, type.titleArabic, spokenText)
            _aiIsAuditing.value = false
            _aiAuditFeedback.value = result.feedback

            if (result.isValid) {
                updateAutobusAnswer(type, result.cleanedWord)
                audioEffects.playCorrect()
            } else {
                audioEffects.playWrong()
            }
        }
    }

    /**
     * Audits a spoken answer against a target word or answer using Gemini AI
     * with high-precision local Arabic NLP fallback.
     */
    fun auditSpokenAnswerAgainstTarget(
        expectedWordOrAnswer: String,
        spokenAnswer: String,
        questionOrContext: String = "",
        acceptableSynonyms: List<String> = emptyList(),
        onResult: ((AiSpokenAnswerEvaluation) -> Unit)? = null
    ) {
        if (spokenAnswer.isBlank()) return
        viewModelScope.launch {
            _aiIsAuditing.value = true
            audioEffects.playTick()
            val evaluation = GeminiSpeechAuditor.auditSpokenAnswerAgainstTarget(
                expectedWordOrAnswer = expectedWordOrAnswer,
                spokenAnswer = spokenAnswer,
                questionOrContext = questionOrContext,
                acceptableSynonyms = acceptableSynonyms
            )
            _aiIsAuditing.value = false
            _lastSpokenAnswerEvaluation.value = evaluation
            _aiAuditFeedback.value = evaluation.feedback

            if (evaluation.isCorrect) {
                audioEffects.playVictory()
            } else {
                audioEffects.playWrong()
            }
            onResult?.invoke(evaluation)
        }
    }

    /**
     * Spoken Guess for Taboo:
     * When teammate speaks the guessed word into the mic, Gemini AI validates
     * if it matches currentCard.mainWord (handles synonyms, dialects, phonetic variations).
     */
    fun auditTabooSpokenGuess(spokenGuess: String) {
        if (spokenGuess.isBlank()) return
        val currentCard = _tabooCurrent.value
        viewModelScope.launch {
            _aiIsAuditing.value = true
            audioEffects.playTick()
            val evaluation = GeminiSpeechAuditor.auditSpokenAnswerAgainstTarget(
                expectedWordOrAnswer = currentCard.mainWord,
                spokenAnswer = spokenGuess,
                questionOrContext = "تخمين الكلمة المطلوبة في لعبة التابو العربي - قسم ${currentCard.category}"
            )
            _aiIsAuditing.value = false
            _lastSpokenAnswerEvaluation.value = evaluation
            _aiAuditFeedback.value = evaluation.feedback

            if (evaluation.isCorrect) {
                audioEffects.playVictory()
                _tabooScore.value += 1
                repository.addPointsAndCoins(35, 15)
                recordPartyGameScore(
                    "التابو العربي",
                    "party",
                    35,
                    "تخمين صوتي ذكي للكلمة (${currentCard.mainWord}) تدقيق الذكاء الاصطناعي 🎙️🎯"
                )
                nextTabooCard()
            } else {
                audioEffects.playWrong()
            }
        }
    }

    /**
     * Spoken answer for Trivia and Questions:
     * Validates spoken voice answers against correct questions.
     */
    fun auditQuestionVoiceAnswer(
        question: String,
        correctAnswer: String,
        spokenAnswer: String,
        acceptableSynonyms: List<String> = emptyList(),
        onSuccess: () -> Unit
    ) {
        if (spokenAnswer.isBlank()) return
        viewModelScope.launch {
            _aiIsAuditing.value = true
            audioEffects.playTick()
            val evaluation = GeminiSpeechAuditor.auditSpokenAnswerAgainstTarget(
                expectedWordOrAnswer = correctAnswer,
                spokenAnswer = spokenAnswer,
                questionOrContext = question,
                acceptableSynonyms = acceptableSynonyms
            )
            _aiIsAuditing.value = false
            _lastSpokenAnswerEvaluation.value = evaluation
            _aiAuditFeedback.value = evaluation.feedback

            if (evaluation.isCorrect) {
                audioEffects.playVictory()
                onSuccess()
            } else {
                audioEffects.playWrong()
            }
        }
    }

    val userProfile: StateFlow<UserProfile> = repository.userProfile
    val isOfflineMode: StateFlow<Boolean> = offlineRepository.isOfflineMode
    val isSyncing: StateFlow<Boolean> = offlineRepository.isSyncing
    val pendingSyncCount: StateFlow<Int> = offlineRepository.pendingSyncCount
    val cachedOfflineQuestionsCount: StateFlow<Int> = offlineRepository.cachedQuestionCount
    val lastSyncResult: StateFlow<OfflineSyncResult?> = offlineRepository.lastSyncResult

    private val _showOfflineDialog = MutableStateFlow(false)
    val showOfflineDialog: StateFlow<Boolean> = _showOfflineDialog.asStateFlow()

    fun setShowOfflineDialog(show: Boolean) {
        audioEffects.playClick()
        _showOfflineDialog.value = show
    }

    fun toggleOfflineMode() {
        audioEffects.playClick()
        offlineRepository.toggleOfflineMode()
    }

    fun syncOfflineRecords() {
        audioEffects.playWhistle()
        viewModelScope.launch {
            val result = offlineRepository.syncPendingRecords()
            if (result.syncedCount > 0) {
                audioEffects.playCorrect()
            }
        }
    }

    fun dismissLastSyncResult() {
        offlineRepository.dismissLastSyncResult()
    }

    // =========================================================================
    // ONLINE GAME ROOMS: إنشاء الغرف وقائمة المشاركين (Supabase + كاش محلي)
    // =========================================================================
    /**
     * قائمة الغرف من الكاش المحلي عبر Flow (Offline-first).
     * إذا كانت القائمة فارغة نعرض غرفاً تجريبية حتى تبقى الشاشة حيّة بدون سيرفر.
     */
    val onlineRooms: StateFlow<List<GameRoom>> = gameRoomRepository.observeRooms()
        .map { rooms -> if (rooms.isEmpty()) demoOnlineRooms() else rooms }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), demoOnlineRooms())

    private val _onlineRoomParticipants = MutableStateFlow<List<RoomParticipant>>(emptyList())
    val onlineRoomParticipants: StateFlow<List<RoomParticipant>> = _onlineRoomParticipants.asStateFlow()

    private val _selectedRoomCode = MutableStateFlow<String?>(null)
    val selectedRoomCode: StateFlow<String?> = _selectedRoomCode.asStateFlow()

    private val _isLoadingParticipants = MutableStateFlow(false)
    val isLoadingParticipants: StateFlow<Boolean> = _isLoadingParticipants.asStateFlow()

    private val _isRoomActionBusy = MutableStateFlow(false)
    val isRoomActionBusy: StateFlow<Boolean> = _isRoomActionBusy.asStateFlow()

    private val _roomActionMessage = MutableStateFlow<String?>(null)
    val roomActionMessage: StateFlow<String?> = _roomActionMessage.asStateFlow()

    private val _lastCreatedRoomCode = MutableStateFlow<String?>(null)
    val lastCreatedRoomCode: StateFlow<String?> = _lastCreatedRoomCode.asStateFlow()

    /** كود مقترح يظهر في حوار الإنشاء، ويتأكد المستودع من فرادته قبل الاعتماد. */
    private val _roomPreviewCode = MutableStateFlow(gameRoomRepository.generateRoomCode())
    val roomPreviewCode: StateFlow<String> = _roomPreviewCode.asStateFlow()

    fun regenerateRoomCodePreview() {
        _roomPreviewCode.value = gameRoomRepository.generateRoomCode()
    }

    fun dismissRoomActionMessage() {
        _roomActionMessage.value = null
    }

    /** سحب أحدث الغرف من Supabase وتحديث الكاش المحلي. */
    fun loadOnlineRooms(limit: Int = GameRoomRepository.DEFAULT_FETCH_LIMIT) {
        viewModelScope.launch {
            _isRoomActionBusy.value = true
            gameRoomRepository.refreshRooms(limit)
            _isRoomActionBusy.value = false
        }
    }

    /** إنشاء غرفة جديدة باسم المضيف الحالي وإضافته كأول مشارك. */
    fun createOnlineRoom(gameType: String, preferredCode: String?) {
        viewModelScope.launch {
            _isRoomActionBusy.value = true
            val profile = repository.userProfile.value
            val result = gameRoomRepository.createRoom(
                hostName = profile.playerName,
                gameType = gameType,
                hostAvatar = profile.avatarEmoji,
                preferredCode = preferredCode
            )
            _isRoomActionBusy.value = false

            val room = result.getOrNull()
            if (room != null) {
                audioEffects.playWhistle()
                _lastCreatedRoomCode.value = room.roomCode
                _roomPreviewCode.value = gameRoomRepository.generateRoomCode()
                _roomActionMessage.value = if (room.isOfflineOnly) {
                    "تم إنشاء الغرفة (${room.roomCode}) محلياً 📴 ستُزامَن مع السيرفر تلقائياً"
                } else {
                    "تم إنشاء الغرفة (${room.roomCode}) بنجاح ⚽ شارك الكود مع أصحابك"
                }
                _selectedRoomCode.value = room.roomCode
                loadParticipants(room.roomCode)
            } else {
                audioEffects.playWrong()
                _roomActionMessage.value = result.failureMessageOrNull() ?: "تعذّر إنشاء الغرفة ⛔"
            }
        }
    }

    /** انضمام لاعب لغرفة بكودها، ثم [onJoined] عند النجاح. */
    fun joinOnlineRoom(roomCode: String, onJoined: () -> Unit = {}) {
        viewModelScope.launch {
            _isRoomActionBusy.value = true
            val profile = repository.userProfile.value
            val result = gameRoomRepository.joinRoom(
                roomCode = roomCode,
                playerName = profile.playerName,
                avatarEmoji = profile.avatarEmoji
            )
            _isRoomActionBusy.value = false

            val room = result.getOrNull()
            if (room != null) {
                audioEffects.playWhistle()
                _selectedRoomCode.value = room.roomCode
                _roomActionMessage.value = "انضممت إلى الغرفة (${room.roomCode}) 🎮"
                loadParticipants(room.roomCode)
                onJoined()
            } else {
                audioEffects.playWrong()
                _roomActionMessage.value = result.failureMessageOrNull() ?: "تعذّر الانضمام للغرفة ⛔"
            }
        }
    }

    /** فتح قائمة مشاركي غرفة (من السيرفر مع رجوع تلقائي للكاش). */
    fun openRoomParticipants(roomCode: String) {
        audioEffects.playClick()
        _selectedRoomCode.value = roomCode
        viewModelScope.launch { loadParticipants(roomCode) }
    }

    fun refreshRoomParticipants() {
        val code = _selectedRoomCode.value ?: return
        viewModelScope.launch { loadParticipants(code) }
    }

    fun closeRoomParticipants() {
        _selectedRoomCode.value = null
        _onlineRoomParticipants.value = emptyList()
    }

    private suspend fun loadParticipants(roomCode: String) {
        _isLoadingParticipants.value = true
        val result = gameRoomRepository.listParticipants(roomCode)
        _onlineRoomParticipants.value = result.getOrNull().orEmpty()
        _isLoadingParticipants.value = false
    }

    /** تبديل جاهزية مشارك ثم تحديث القائمة. */
    fun toggleParticipantReady(participant: RoomParticipant) {
        viewModelScope.launch {
            gameRoomRepository.setParticipantReady(
                roomCode = participant.roomCode,
                playerName = participant.playerName,
                isReady = !participant.isReady
            )
            loadParticipants(participant.roomCode)
        }
    }

    /** دفع الغرف والمشاركين المُنشأة أوفلاين إلى Supabase. */
    fun syncPendingOnlineRooms() {
        viewModelScope.launch {
            val synced = gameRoomRepository.syncPendingRooms()
            if (synced > 0) {
                audioEffects.playCorrect()
                _roomActionMessage.value = "تمت مزامنة $synced غرفة مع السيرفر ☁️"
            }
        }
    }

    // =========================================================================
    // CLOUD USERS & LEADERBOARD: جداول users و leaderboard في Supabase
    // =========================================================================
    /** أعلى النتائج من view `public.leaderboard`. */
    private val _cloudLeaderboard = MutableStateFlow<List<SupabaseScore>>(emptyList())
    val cloudLeaderboard: StateFlow<List<SupabaseScore>> = _cloudLeaderboard.asStateFlow()

    /** ملف اللاعب الحالي من view `public.users` (إجمالي نقاطه وتحدياته). */
    private val _cloudUserProfile = MutableStateFlow<SupabaseUser?>(null)
    val cloudUserProfile: StateFlow<SupabaseUser?> = _cloudUserProfile.asStateFlow()

    private val _isCloudLeaderboardLoading = MutableStateFlow(false)
    val isCloudLeaderboardLoading: StateFlow<Boolean> = _isCloudLeaderboardLoading.asStateFlow()

    /** سحب لوحة الصدارة وملف اللاعب من Supabase. */
    fun loadCloudLeaderboard() {
        viewModelScope.launch { loadCloudLeaderboardData() }
    }

    private suspend fun loadCloudLeaderboardData() {
        _isCloudLeaderboardLoading.value = true
        val userDao = SupabaseClientProvider.userDao()

        val scores = runCatching { userDao.fetchLeaderboard(CLOUD_LEADERBOARD_LIMIT) }.getOrNull()
        _cloudLeaderboard.value = if (!scores.isNullOrEmpty()) scores else demoCloudScores()

        val profileName = repository.userProfile.value.playerName
        _cloudUserProfile.value = runCatching { userDao.fetchUserByName(profileName) }.getOrNull()

        _isCloudLeaderboardLoading.value = false
    }

    /**
     * تسجيل اللاعب الحالي في الجدول السحابي عبر الدالة البسيطة [addNewPlayer]
     * (إدراج في `leaderboard` ⇒ يظهر تلقائياً في `users`).
     */
    fun registerPlayerInCloud() {
        viewModelScope.launch {
            _isCloudLeaderboardLoading.value = true
            val profile = repository.userProfile.value

            val registered = runCatching {
                addNewPlayer(
                    playerName = profile.playerName,
                    points = profile.points,
                    coins = profile.coins,
                    levelTitle = profile.levelTitle,
                    favoriteClub = profile.favoriteClub,
                    challengeType = "general"
                )
            }.getOrDefault(false)

            _isCloudLeaderboardLoading.value = false

            if (registered) {
                audioEffects.playVictory()
                _roomActionMessage.value = "تم تسجيلك في لوحة الصدارة العالمية 🌍🏆"
                loadCloudLeaderboardData()
            } else {
                audioEffects.playWrong()
                _roomActionMessage.value = "تعذّر التسجيل — تأكد من إعدادات Supabase 🔌"
            }
        }
    }

    // =========================================================================
    // LIVE ROOM MATCH SESSION — تزامن اللعب نفسه بين الأجهزة
    // =========================================================================
    // الفكرة: أي لعبة موجودة بتسجّل نقاطها عبر recordPartyGameScore (15 موضعاً)،
    // لذلك نربط الدفع للغرفة هناك مرة واحدة ⇒ كل الألعاب تتزامن تلقائياً
    // بدون تعديل أي شاشة لعبة.
    // =========================================================================

    /** كود الغرفة الحالية، أو null لو مش جوه مباراة. */
    private val _activeRoomCode = MutableStateFlow<String?>(null)
    val activeRoomCode: StateFlow<String?> = _activeRoomCode.asStateFlow()

    private val _activeRoom = MutableStateFlow<GameRoom?>(null)
    val activeRoom: StateFlow<GameRoom?> = _activeRoom.asStateFlow()

    private val _activeRoomParticipants = MutableStateFlow<List<RoomParticipant>>(emptyList())
    val activeRoomParticipants: StateFlow<List<RoomParticipant>> = _activeRoomParticipants.asStateFlow()

    /** حصيلة الفرق (أحمر/أزرق) محسوبة لحظياً من المشاركين. */
    private val _roomTeamScores = MutableStateFlow(RoomTeamScores.EMPTY)
    val roomTeamScores: StateFlow<RoomTeamScores> = _roomTeamScores.asStateFlow()

    /** هل اللاعب الحالي هو مضيف الغرفة؟ (يظهر له أزرار التحكم بالجولات) */
    private val _isRoomSessionHost = MutableStateFlow(false)
    val isRoomSessionHost: StateFlow<Boolean> = _isRoomSessionHost.asStateFlow()

    private var roomSessionJob: Job? = null

    /**
     * عميل الاشتراك اللحظي (Supabase Realtime / Phoenix WebSocket).
     *
     * بيتبني مرة واحدة وبيفضل عائم لحد ما جلسه تبدأ. البناء نفسه رخيص
     * (مفيش socket لحد [SupabaseRealtimeClient.subscribeToRoom]).
     */
    private val realtimeClient = SupabaseRealtimeClient(
        onEvent = { event -> onRealtimeChange(event) }
    )

    /** هل الدفع اللحظي شغال؟ (يُعرض في الواجهة بدل رقم Ping وهمي) */
    val realtimeConnected: StateFlow<Boolean> = realtimeClient.isConnected

    /**
     * حالة الاشتراك اللحظي — بتمرر من العميل نفسه مباشرةً، لأن العميل
     * بيحدّثها في كل المسارات (نجاح/رفض/انقطاع) ومفيش سبب إن الـVM يعمل نسخة.
     */
    val realtimeStatus: StateFlow<String?> = realtimeClient.statusMessage

    /** تجميع الانفجارات: عدة تغييرات في نفس اللحظة = تحديث واحد. */
    private var realtimeRefreshJob: Job? = null

    private fun onRealtimeChange(event: RealtimeEvent) {
        if (!event.isRoomChange) return
        realtimeRefreshJob?.cancel()
        realtimeRefreshJob = viewModelScope.launch {
            delay(REALTIME_REFRESH_DEBOUNCE_MS)
            syncRoomSessionOnce()
        }
    }

    /**
     * الاشتراك اللحظي بيُفعّل على السحابة بس: في وضع الهوتسبوت مفيش
     * Supabase من الأساس، والدفع بيتم من الـkeep-alive بتاع السيرفر المحلي.
     */
    private fun startRealtimeSubscription(roomCode: String) {
        if (!SupabaseClientProvider.isConfigured() || LanGameManager.isLanActive()) {
            // مفيش سحابة نتشترك معاها: نمسح أي رسالة قديمة وسِب الحلبة
            // الدورية تشتغل، بدل ما اللاعب يقرا «اتصال لحظي شغّال» كاذبة
            realtimeClient.clearStatus()
            return
        }
        // العميل نفسه بيحط رسالة عربية في كل مسار فشل، فمفيش حاجة تانية نقولها هنا
        realtimeClient.subscribeToRoom(roomCode)
    }

    private fun stopRealtimeSubscription() {
        realtimeRefreshJob?.cancel()
        realtimeRefreshJob = null
        realtimeClient.unsubscribe()
        realtimeClient.clearStatus()
    }

    /**
     * دخول غرفة وبدء جلسة متزامنة: يفتح شاشة المباراة ويشغّل حلقة تحديث
     * تجيب حالة الغرفة والمشاركين كل [ROOM_SESSION_POLL_MS] مللي ثانية،
     * كطريق احتياطي لو الاشتراك اللحظي ماشتغلش.
     */
    fun enterRoom(room: GameRoom) {
        audioEffects.playWhistle()
        _activeRoomCode.value = room.roomCode
        _activeRoom.value = room
        _isRoomSessionHost.value = isCurrentPlayer(room.hostName)
        navigateTo(GameScreen.RoomMatch)
        startRoomSessionLoop()
        startRealtimeSubscription(room.roomCode)
    }

    /** دخول بالكود مباشرةً (بعد joinRoom) بدون الحاجة لكائن الغرفة في الشاشة. */
    fun enterRoomByCode(roomCode: String) {
        viewModelScope.launch {
            val room = gameRoomRepository.getRoom(roomCode)
            if (room != null) {
                enterRoom(room)
            } else {
                _roomActionMessage.value = GameRoomRepository.ERROR_ROOM_NOT_FOUND
            }
        }
    }

    /** الخروج من الغرفة وإيقاف حلقة التحديث والاشتراك اللحظي (الغرفة نفسها ما تتحذفش). */
    fun leaveRoom() {
        roomSessionJob?.cancel()
        roomSessionJob = null
        stopRealtimeSubscription()
        _activeRoomCode.value = null
        _activeRoom.value = null
        _activeRoomParticipants.value = emptyList()
        _roomTeamScores.value = RoomTeamScores.EMPTY
        _isRoomSessionHost.value = false

        // نرجع للخلف (pop) بدل دفع شاشة جديدة فوق المكدس، وإلا زر الرجوع
        // هيفضل يلف بين نسختين من شاشة الغرف
        if (_currentScreen.value == GameScreen.RoomMatch) {
            navigateBack()
        } else {
            navigateTo(GameScreen.SupabaseOnlineScreen)
        }
    }

    private fun startRoomSessionLoop() {
        roomSessionJob?.cancel()
        roomSessionJob = viewModelScope.launch {
            // أول تحديث فوري حتى لا تشاهد الشاشة حالة قديمة
            syncRoomSessionOnce()
            while (isActive) {
                // لو الدفع اللحظي شغال فالحلقة مجرد شبكة أمان ⇒ تبطّئ،
                // وإلا هي مصدر التحديث الوحيد ⇒ تسريع
                val waitMs = if (realtimeClient.isConnected.value) {
                    ROOM_SESSION_IDLE_POLL_MS
                } else {
                    ROOM_SESSION_POLL_MS
                }
                delay(waitMs)
                syncRoomSessionOnce()
            }
        }
    }

    /** سحب حالة الغرفة + المشاركين مرة واحدة وتحديث الحصيلة. */
    private suspend fun syncRoomSessionOnce() {
        val code = _activeRoomCode.value ?: return
        val room = gameRoomRepository.getRoom(code)
        if (room != null) _activeRoom.value = room

        val participants = gameRoomRepository.listParticipants(code).getOrNull().orEmpty()
        _activeRoomParticipants.value = participants
        _roomTeamScores.value = RoomTeamScores.from(participants)
    }

    /** تحديث يدوي من الواجهة (زر 🔄). */
    fun refreshRoomSession() {
        viewModelScope.launch { syncRoomSessionOnce() }
    }

    // ------------------------- تحكم المضيف بالجولات -------------------------

    fun hostStartGame() {
        viewModelScope.launch {
            val code = _activeRoomCode.value ?: return@launch
            audioEffects.playWhistle()
            gameRoomRepository.startGame(code)
            syncRoomSessionOnce()
        }
    }

    fun hostNextRound() {
        viewModelScope.launch {
            val code = _activeRoomCode.value ?: return@launch
            audioEffects.playClick()
            gameRoomRepository.advanceRound(code)
            syncRoomSessionOnce()
        }
    }

    fun hostFinishGame() {
        viewModelScope.launch {
            val code = _activeRoomCode.value ?: return@launch
            audioEffects.playVictory()
            gameRoomRepository.finishGame(code)
            syncRoomSessionOnce()
        }
    }

    /**
     * تشغيل جولة اللعبة الفعلية حسب نوع الغرفة.
     * النقاط اللي هتسجّلها اللعبة هتتدفع للغرفة تلقائياً عبر
     * [recordPartyGameScore] ⇒ باقي الأجهزة تشوفها في التحديث التالي.
     */
    fun launchRoomRound() {
        val room = _activeRoom.value ?: return
        audioEffects.playClick()
        when (room.gameType) {
            "thirty_seconds" -> startThirtySecondsQuiz()
            "buzzer" -> navigateTo(GameScreen.ThirtySecondsRoundsScreen)
            "tictactoe" -> navigateTo(GameScreen.FootballTicTacToeScreen)
            "auction" -> navigateTo(GameScreen.FootballAuctionScreen)
            "sabaho" -> startSabahoChallenge()
            else -> startPvPMatchSearch()
        }
    }

    /** رجوع لشاشة الغرفة بعد ما تخلّص الجولة (بدون إنهاء الجلسة). */
    fun backToRoomMatch() {
        if (_activeRoomCode.value != null) navigateTo(GameScreen.RoomMatch) else navigateBack()
    }

    /**
     * دفع نقاط اللاعب الحالي للغرفة (تُستدعى تلقائياً من [recordPartyGameScore]).
     * لو مش جوه غرفة ⇒ لا شيء يحدث، فالألعاب العادية تتصرف زي ما هي.
     */
    private suspend fun pushScoreToActiveRoom(earnedScore: Int) {
        val code = _activeRoomCode.value ?: return
        if (earnedScore <= 0) return

        val myName = repository.userProfile.value.playerName
        val me = _activeRoomParticipants.value.firstOrNull {
            it.playerName.trim().equals(myName.trim(), ignoreCase = true)
        } ?: return

        gameRoomRepository.updateParticipantScore(code, me.playerName, me.score + earnedScore)
        syncRoomSessionOnce()
    }

    private fun isCurrentPlayer(name: String): Boolean =
        name.trim().equals(repository.userProfile.value.playerName.trim(), ignoreCase = true)

    // =========================================================================
    // LIVE QUESTION FEED — أسئلة البوت توصل اللعبة فعلاً
    // =========================================================================
    // كل الأسئلة كانت مكتوبة listOf(...) جوه FootballRepository، يعني مقفولة
    // داخل الـAPK: أي سؤال بيضيفه البوت في Supabase ما كانش بيوصل اللاعب.
    // الـStateFlows دي بتقرأ الكاش المحلي (Room) مدموجاً مع القائمة المضمّنة،
    // فاللعبة تاخد الأحدث دايمًا وترجع للمضمّن لو السحابة فاضية أو النت واقع.
    // =========================================================================

    private val questionFeed = QuestionFeedRepository.get(application)

    private val _questionFeedStatus = MutableStateFlow<QuestionFeedRepository.SyncResult?>(null)
    val questionFeedStatus: StateFlow<QuestionFeedRepository.SyncResult?> = _questionFeedStatus.asStateFlow()

    val liveThirtySeconds: StateFlow<List<ThirtySecondsQuestion>> =
        questionFeed.thirtySeconds(repository.getThirtySecondsQuestions())
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                repository.getThirtySecondsQuestions()
            )

    val liveSabaho: StateFlow<List<SabahoQuestion>> =
        questionFeed.sabaho(repository.getSabahoQuestions())
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                repository.getSabahoQuestions()
            )

    val liveTrueFalse: StateFlow<List<TrueFalseQuestion>> =
        questionFeed.trueFalse(repository.getTrueFalseQuestions())
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                repository.getTrueFalseQuestions()
            )

    val livePvp: StateFlow<List<PvPQuestion>> =
        questionFeed.pvp(repository.getPvPQuestions())
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                repository.getPvPQuestions()
            )

    /** عدد الأسئلة السحابية المخزّنة محلياً — للعرض في الإعدادات. */
    val cloudQuestionCount: StateFlow<Int> = questionFeed.observeCloudCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** آخر مرة جابنا فيها من السيرفر — عشان ما نفتحش طلب كل ما اللاعب يفتح لعبة. */
    private var lastFeedRefreshAtMillis = 0L

    init {
        // أول مزامنة عند تجهيز الـViewModel (بتغطي فتح التطبيق وتغيير الشبكة)
        maybeRefreshQuestionFeed()
    }

    /**
     * مزامنة لو عدّت [QUESTION_FEED_MIN_REFRESH_MS] على آخر محاولة.
     * بتتنادى قبل بدء أي لعبة، فمفيش حاجة تانية محتاجها في الخلفية.
     */
    fun maybeRefreshQuestionFeed() {
        val now = System.currentTimeMillis()
        if (now - lastFeedRefreshAtMillis < QUESTION_FEED_MIN_REFRESH_MS) return
        lastFeedRefreshAtMillis = now
        viewModelScope.launch {
            _questionFeedStatus.value = questionFeed.refresh()
        }
    }

    /** مزامنة قسرية من شاشة الإعدادات (زر «حدّث الأسئلة»). */
    fun forceRefreshQuestionFeed() {
        lastFeedRefreshAtMillis = System.currentTimeMillis()
        viewModelScope.launch {
            audioEffects.playClick()
            _questionFeedStatus.value = questionFeed.refresh()
        }
    }

    fun dismissQuestionFeedStatus() {
        _questionFeedStatus.value = null
    }

    /** وصف عربي لحالة الأسئلة — بيشمل عدد أسئلة البوت اللي وصلت فعلاً. */
    fun questionFeedSummary(): String =
        QuestionFeedLogic.describeFeed(
            cloudCount = cloudQuestionCount.value,
            builtinCount = repository.getThirtySecondsQuestions().size +
                repository.getSabahoQuestions().size +
                repository.getTrueFalseQuestions().size +
                repository.getPvPQuestions().size
        )


    // =========================================================================
    // LAN / HOTSPOT MULTIPLAYER — لعب على الشبكة المحلية بدون إنترنت
    // =========================================================================
    // الحالة مملوكة لـ LanGameManager (لأنها بتعيش أطول من الـViewModel:
    // السيرفر لازم يفضل شغّال لو اتلفّت الشاشة)، والـViewModel بيمرّرها
    // للواجهة وبيغلّف الأوامر — بنفس نمط باقي الـflows في المشروع.
    // =========================================================================

    init {
        // المضيف بيتعرّف على نفسه باسم اللاعب الحالي، والقيم بتتقرا لحظياً
        // عشان لو اللاعب غيّر اسمه بعدين السيرفر يستخدم الاسم الجديد
        LanGameManager.currentNameProvider = { repository.userProfile.value.playerName }
        LanGameManager.currentAvatarProvider = { repository.userProfile.value.avatarEmoji }
    }

    val lanIsHosting: StateFlow<Boolean> = LanGameManager.isHosting
    val lanHostAddress: StateFlow<String?> = LanGameManager.hostAddress
    val lanHostPort: StateFlow<Int> = LanGameManager.hostPort
    val lanDiscoveredHosts: StateFlow<List<DiscoveredHost>> = LanGameManager.discoveredHosts
    val lanIsDiscovering: StateFlow<Boolean> = LanGameManager.isDiscovering
    val lanJoinedHost: StateFlow<String?> = LanGameManager.joinedHost
    val lanIsBusy: StateFlow<Boolean> = LanGameManager.isBusy
    val lanMessage: StateFlow<String?> = LanGameManager.lanMessage
    val lanConnectedClientCount: StateFlow<Int> = LanGameManager.connectedClientCount

    /** تشغيل سيرفر اللعبة على الجهاز ده (الهوتسبوت أو الواي فاي المنزلي). */
    fun startLanHosting(port: Int = LanProtocol.DEFAULT_GAME_PORT) {
        viewModelScope.launch {
            audioEffects.playClick()
            val message = LanGameManager.startHosting(port)
            if (message.contains("شغّال")) {
                audioEffects.playWhistle()
                // نحدّث قائمة الغرف عشان الغرفة المحلية تظهر فوراً
                loadOnlineRooms()
            }
        }
    }

    /** إيقاف السيرفر المحلي. */
    fun stopLanHosting() {
        audioEffects.playClick()
        LanGameManager.stopHosting()
        loadOnlineRooms()
    }

    /** الاتصال بسيرفر مضيف بعنوان IP (الطريقة اليدوية الاحتياطية). */
    fun connectToLanHost(hostIp: String, port: Int = LanProtocol.DEFAULT_GAME_PORT) {
        viewModelScope.launch {
            audioEffects.playClick()
            val message = LanGameManager.connectToHost(hostIp, port)
            if (message.startsWith("اتصلت")) {
                audioEffects.playVictory()
                loadOnlineRooms()
            }
        }
    }

    /** الاتصال بسيرفر مكتشف تلقائياً. */
    fun connectToDiscoveredHost(host: DiscoveredHost) {
        connectToLanHost(host.hostIp, host.port)
    }

    /** قطع الاتصال بسيرفر مضيف. */
    fun disconnectFromLanHost() {
        audioEffects.playClick()
        LanGameManager.disconnectFromHost()
        loadOnlineRooms()
    }

    /** بدء البحث التلقائي عن سيرفرات على نفس الشبكة (بثّ UDP). */
    fun startLanDiscovery() {
        LanGameManager.startDiscovery()
    }

    /** إعادة البحث التلقائي. */
    fun refreshLanDiscovery() {
        audioEffects.playClick()
        LanGameManager.refreshDiscovery()
    }

    /** إيقاف البحث التلقائي. */
    fun stopLanDiscovery() {
        LanGameManager.stopDiscovery()
    }

    fun dismissLanMessage() {
        LanGameManager.dismissMessage()
    }

    /** وصف عربي كامل لحالة الشبكة المحلية — يُعرض في واجهة الهوتسبوت. */
    fun describeLanStatus(): String = LanGameManager.describeStatus()

    /** عنوان IP لجهاز اللاعب — يُقرأ للأصحاب لو الاكتشاف التلقائي فشل. */
    fun myLanIpAddress(): String? = LanGameManager.myIpAddress()

    /** هل العنوان ده على نفس شبكة الجهاز؟ (تلميح قبل محاولة الاتصال) */
    fun isSameSubnetAsDevice(remoteIp: String?): Boolean =
        LanGameManager.isSameSubnetAsDevice(remoteIp)

    /** تحديث عدد المتصلين بالسيرفر (يستدعى دورياً من الواجهة). */
    fun refreshLanClientCount() {
        LanGameManager.updateClientCount()
    }


    // =========================================================================
    // ROOM PERSISTENCE: CUMULATIVE PARTY & GATHERING LEADERBOARD
    // =========================================================================
    private val _leaderboardCategoryFilter = MutableStateFlow("all") // "all", "party", "football"
    val leaderboardCategoryFilter: StateFlow<String> = _leaderboardCategoryFilter.asStateFlow()

    private val _leaderboardTab = MutableStateFlow(0) // 0: الترتيب التراكمي, 1: الأرقام القياسية, 2: سجل المباريات
    val leaderboardTab: StateFlow<Int> = _leaderboardTab.asStateFlow()

    fun setLeaderboardCategoryFilter(filter: String) {
        audioEffects.playClick()
        _leaderboardCategoryFilter.value = filter
    }

    fun setLeaderboardTab(tab: Int) {
        audioEffects.playClick()
        _leaderboardTab.value = tab
    }

    val cumulativePartyLeaderboard: StateFlow<List<CumulativePlayerStat>> =
        combine(offlineRepository.observeAllPartyRecords(), _leaderboardCategoryFilter) { records, filter ->
            val user = repository.userProfile.value
            val filtered = if (filter == "all") records else records.filter { it.category == filter }
            filtered.groupBy { it.playerName }.map { (name, playerRecords) ->
                val total = playerRecords.sumOf { it.score }
                val count = playerRecords.size
                val maxScore = playerRecords.maxOfOrNull { it.score } ?: 0
                val favGame = playerRecords.groupBy { it.gameType }
                    .maxByOrNull { it.value.size }?.key ?: "ألعاب الصالون"
                val avatar = playerRecords.firstOrNull()?.playerAvatar ?: "👤"
                val isCurrent = name.trim().equals(user.playerName.trim(), ignoreCase = true)

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
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val gameHighScores: StateFlow<List<GameHighScoreStat>> =
        offlineRepository.observeGameHighScores()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPartyRecords: StateFlow<List<PartyLeaderboardRecordEntity>> =
        offlineRepository.observeAllPartyRecords()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun recordPartyGameScore(gameType: String, category: String, score: Int, details: String = "") {
        val user = userProfile.value
        viewModelScope.launch {
            offlineRepository.recordPartyGameScore(
                playerName = user.playerName,
                playerAvatar = user.avatarEmoji,
                gameType = gameType,
                category = category,
                score = score,
                details = details
            )
            // لو اللاعب جوه غرفة ⇒ نفس النقاط تتدفع للغرفة فيشوفها باقي الأجهزة
            pushScoreToActiveRoom(score)

            val newlyCompleted = dailyChallengeManager.reportGameScore(gameType, score)
            _dailyChallenge.value = dailyChallengeManager.getTodayChallenge()
            if (newlyCompleted) {
                audioEffects.playVictory()
            }
        }
    }

    fun addManualPartyRecord(
        playerName: String,
        playerAvatar: String,
        gameType: String,
        category: String,
        score: Int,
        details: String
    ) {
        audioEffects.playVictory()
        viewModelScope.launch {
            offlineRepository.recordPartyGameScore(
                playerName = playerName,
                playerAvatar = playerAvatar,
                gameType = gameType,
                category = category,
                score = score,
                details = details
            )
            val newlyCompleted = dailyChallengeManager.reportGameScore(gameType, score)
            _dailyChallenge.value = dailyChallengeManager.getTodayChallenge()
            if (newlyCompleted) {
                audioEffects.playVictory()
            }
        }
    }

    fun resetPartyLeaderboard() {
        audioEffects.playWhistle()
        viewModelScope.launch {
            offlineRepository.resetPartyLeaderboardWithPresets()
        }
    }

    // -------------------------------------------------------------------------
    // FRIEND INVITE & CHALLENGE CODES (دعوة صديق ومواجهات ودية)
    // -------------------------------------------------------------------------
    private val _userChallengeCode = MutableStateFlow(generateChallengeCode())
    val userChallengeCode: StateFlow<String> = _userChallengeCode.asStateFlow()

    private val _enteredFriendCode = MutableStateFlow("")
    val enteredFriendCode: StateFlow<String> = _enteredFriendCode.asStateFlow()

    private val _friendlyFriendsList = MutableStateFlow(defaultPresetFriends())
    val friendlyFriendsList: StateFlow<List<FriendlyChallengeInfo>> = _friendlyFriendsList.asStateFlow()

    private val _friendlyMatchHistory = MutableStateFlow(defaultFriendlyHistory())
    val friendlyMatchHistory: StateFlow<List<FriendlyMatchHistoryItem>> = _friendlyMatchHistory.asStateFlow()

    fun generateNewChallengeCode() {
        audioEffects.playClick()
        _userChallengeCode.value = generateChallengeCode()
    }

    fun setEnteredFriendCode(code: String) {
        _enteredFriendCode.value = code.trim().uppercase()
    }

    fun shareChallengeCode(context: Context) {
        audioEffects.playClick()
        val code = _userChallengeCode.value
        val shareText = """
            ⚽🔥 تحداني الآن في تطبيق 'ملعب التحدي'!
            رمز المواجهة الودية الخاص بي هو: $code
            أدخل الرمز في وضع المواجهة الودية لنلعب وجهاً لوجه ونرى من هو كابتن الملعب الحقيقي! 🏆
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "مشاركة رمز التحدي الودي 📲")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun shareMatchResult(context: Context, oppName: String, userScore: Int, oppScore: Int, isWin: Boolean) {
        audioEffects.playClick()
        val resultText = if (isWin) {
            "🏆🔥 حققت فوزاً تاريخياً على كابتن $oppName بنتيجة $userScore - $oppScore في المواجهة الودية بـ 'ملعب التحدي'!"
        } else if (userScore == oppScore) {
            "🤝 تعادل ملحمي ومباراة نارية ضد كابتن $oppName بنتيجة $userScore - $oppScore في 'ملعب التحدي'!"
        } else {
            "⚽ انتهت مباراة ودية رائعة ضد كابتن $oppName بنتيجة $userScore - $oppScore في 'ملعب التحدي'! القادم أفضل! 💪"
        }
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, resultText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "مشاركة نتيجة المواجهة الودية 📲")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    private val _currentScreen = MutableStateFlow<GameScreen>(GameScreen.Home)
    val currentScreen: StateFlow<GameScreen> = _currentScreen.asStateFlow()

    // Screen navigation stack
    private val backStack = mutableListOf<GameScreen>(GameScreen.Home)

    fun navigateTo(screen: GameScreen) {
        audioEffects.playClick()
        backStack.add(screen)
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        audioEffects.playClick()
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            _currentScreen.value = backStack.last()
            return true
        }
        return false
    }

    // -------------------------------------------------------------------------
    // Rewarded Ad Simulation Modal State
    // -------------------------------------------------------------------------
    private val _showRewardedAdDialog = MutableStateFlow(false)
    val showRewardedAdDialog: StateFlow<Boolean> = _showRewardedAdDialog.asStateFlow()

    private val _adCountdown = MutableStateFlow(5)
    val adCountdown: StateFlow<Int> = _adCountdown.asStateFlow()

    private var adJob: Job? = null

    fun openRewardedAd() {
        audioEffects.playClick()
        _showRewardedAdDialog.value = true
        _adCountdown.value = 5
        adJob?.cancel()
        adJob = viewModelScope.launch {
            while (_adCountdown.value > 0) {
                delay(1000)
                _adCountdown.value -= 1
            }
            audioEffects.playWhistle()
        }
    }

    fun claimAdReward() {
        if (_adCountdown.value <= 0) {
            repository.rewardFromAd()
            audioEffects.playCorrect()
            _showRewardedAdDialog.value = false
        }
    }

    fun dismissAd() {
        adJob?.cancel()
        _showRewardedAdDialog.value = false
    }

    // -------------------------------------------------------------------------
    // GAME 1: 30 Seconds Speed Quiz
    // -------------------------------------------------------------------------
    private val _tsQuestions = MutableStateFlow<List<ThirtySecondsQuestion>>(emptyList())
    private val _tsCurrentIndex = MutableStateFlow(0)
    val tsCurrentIndex: StateFlow<Int> = _tsCurrentIndex.asStateFlow()

    private val _tsScore = MutableStateFlow(0)
    val tsScore: StateFlow<Int> = _tsScore.asStateFlow()

    private val _tsSecondsRemaining = MutableStateFlow(30)
    val tsSecondsRemaining: StateFlow<Int> = _tsSecondsRemaining.asStateFlow()

    private val _tsIsGameOver = MutableStateFlow(false)
    val tsIsGameOver: StateFlow<Boolean> = _tsIsGameOver.asStateFlow()

    private val _tsSelectedAnswer = MutableStateFlow<Int?>(null)
    val tsSelectedAnswer: StateFlow<Int?> = _tsSelectedAnswer.asStateFlow()

    private var tsTimerJob: Job? = null

    fun startThirtySecondsQuiz() {
        // بنقرأ من الـfeed الحي: أسئلة البوت الأول + المضمّن بدون تكرار
        maybeRefreshQuestionFeed()
        val questions = liveThirtySeconds.value.shuffled()
        _tsQuestions.value = questions
        _tsCurrentIndex.value = 0
        _tsScore.value = 0
        _tsSecondsRemaining.value = 30
        _tsIsGameOver.value = false
        _tsSelectedAnswer.value = null

        navigateTo(GameScreen.ThirtySecondsQuiz)
        audioEffects.playWhistle()

        tsTimerJob?.cancel()
        tsTimerJob = viewModelScope.launch {
            while (_tsSecondsRemaining.value > 0 && !_tsIsGameOver.value) {
                delay(1000)
                _tsSecondsRemaining.value -= 1
                if (_tsSecondsRemaining.value in 1..5) {
                    audioEffects.playTick()
                }
            }
            endThirtySecondsQuiz()
        }
    }

    val currentTsQuestion: ThirtySecondsQuestion?
        get() = _tsQuestions.value.getOrNull(_tsCurrentIndex.value)

    fun answerThirtySecondsQuestion(optionIndex: Int) {
        if (_tsIsGameOver.value || _tsSelectedAnswer.value != null) return
        val current = currentTsQuestion ?: return
        _tsSelectedAnswer.value = optionIndex

        if (optionIndex == current.correctIndex) {
            audioEffects.playCorrect()
            _tsScore.value += 1
        } else {
            audioEffects.playWrong()
        }

        viewModelScope.launch {
            delay(350)
            _tsSelectedAnswer.value = null
            if (_tsCurrentIndex.value + 1 < _tsQuestions.value.size) {
                _tsCurrentIndex.value += 1
            } else {
                _tsQuestions.value = _tsQuestions.value.shuffled()
                _tsCurrentIndex.value = 0
            }
        }
    }

    private fun endThirtySecondsQuiz() {
        _tsIsGameOver.value = true
        tsTimerJob?.cancel()
        audioEffects.playWhistle()
        val earnedScore = _tsScore.value
        val points = earnedScore * 15
        val coins = earnedScore * 5
        repository.addPointsAndCoins(earnedPoints = points, earnedCoins = coins, gameTitle = "تحدي الثلاثين ثانية")
        repository.updateThirtySecondsHighScore(earnedScore)

        if (isOfflineMode.value) {
            viewModelScope.launch {
                offlineRepository.recordOfflineGamePlayed(
                    gameType = "تحدي الـ 30 ثانية",
                    score = earnedScore,
                    earnedPoints = points,
                    earnedCoins = coins
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // GAME 1 MODE B: Player Mention Challenge
    // -------------------------------------------------------------------------
    private val _pmChallenges = MutableStateFlow<List<PlayerMentionChallenge>>(emptyList())
    private val _pmCurrentChallenge = MutableStateFlow<PlayerMentionChallenge?>(null)
    val pmCurrentChallenge: StateFlow<PlayerMentionChallenge?> = _pmCurrentChallenge.asStateFlow()

    private val _pmGuessedPlayers = MutableStateFlow<List<String>>(emptyList())
    val pmGuessedPlayers: StateFlow<List<String>> = _pmGuessedPlayers.asStateFlow()

    private val _pmSecondsRemaining = MutableStateFlow(30)
    val pmSecondsRemaining: StateFlow<Int> = _pmSecondsRemaining.asStateFlow()

    private val _pmIsGameOver = MutableStateFlow(false)
    val pmIsGameOver: StateFlow<Boolean> = _pmIsGameOver.asStateFlow()

    private val _pmFeedbackMessage = MutableStateFlow<String?>(null)
    val pmFeedbackMessage: StateFlow<String?> = _pmFeedbackMessage.asStateFlow()

    private var pmTimerJob: Job? = null

    fun startPlayerMentionChallenge() {
        val challenges = repository.getPlayerMentionChallenges().shuffled()
        _pmChallenges.value = challenges
        _pmCurrentChallenge.value = challenges.firstOrNull()
        _pmGuessedPlayers.value = emptyList()
        _pmSecondsRemaining.value = 30
        _pmIsGameOver.value = false
        _pmFeedbackMessage.value = null

        navigateTo(GameScreen.ThirtySecondsPlayers)
        audioEffects.playWhistle()

        pmTimerJob?.cancel()
        pmTimerJob = viewModelScope.launch {
            while (_pmSecondsRemaining.value > 0 && !_pmIsGameOver.value) {
                delay(1000)
                _pmSecondsRemaining.value -= 1
                if (_pmSecondsRemaining.value in 1..5) {
                    audioEffects.playTick()
                }
            }
            endPlayerMention()
        }
    }

    fun submitPlayerName(input: String) {
        val challenge = _pmCurrentChallenge.value ?: return
        if (_pmIsGameOver.value || input.isBlank()) return

        val matchedName = repository.checkPlayerMatch(input, challenge.acceptedPlayers)
        if (matchedName != null) {
            if (_pmGuessedPlayers.value.contains(matchedName)) {
                _pmFeedbackMessage.value = "تمت إضافته مسبقاً! ⚠️"
                audioEffects.playWrong()
            } else {
                _pmGuessedPlayers.value = _pmGuessedPlayers.value + matchedName
                _pmFeedbackMessage.value = "إجابة صحيحة: $matchedName ⚽"
                audioEffects.playCorrect()
            }
        } else {
            _pmFeedbackMessage.value = "غير موجود في التشكيلة ❌"
            audioEffects.playWrong()
        }

        viewModelScope.launch {
            delay(1500)
            if (_pmFeedbackMessage.value != null) {
                _pmFeedbackMessage.value = null
            }
        }
    }

    private fun endPlayerMention() {
        _pmIsGameOver.value = true
        pmTimerJob?.cancel()
        audioEffects.playWhistle()
        val count = _pmGuessedPlayers.value.size
        repository.addPointsAndCoins(earnedPoints = count * 20, earnedCoins = count * 8)
    }

    // -------------------------------------------------------------------------
    // GAME 2: Sabaho Challenge ("صباحو تحدي")
    // -------------------------------------------------------------------------
    private val _sabahoQuestions = MutableStateFlow<List<SabahoQuestion>>(emptyList())
    private val _sabahoIndex = MutableStateFlow(0)
    val sabahoIndex: StateFlow<Int> = _sabahoIndex.asStateFlow()

    private val _sabahoScore = MutableStateFlow(0)
    val sabahoScore: StateFlow<Int> = _sabahoScore.asStateFlow()

    private val _sabahoTimer = MutableStateFlow(25)
    val sabahoTimer: StateFlow<Int> = _sabahoTimer.asStateFlow()

    private val _sabahoRemovedOptions = MutableStateFlow<Set<Int>>(emptySet())
    val sabahoRemovedOptions: StateFlow<Set<Int>> = _sabahoRemovedOptions.asStateFlow()

    private val _sabahoUsedFiftyFifty = MutableStateFlow(false)
    val sabahoUsedFiftyFifty: StateFlow<Boolean> = _sabahoUsedFiftyFifty.asStateFlow()

    private val _sabahoUsedSkip = MutableStateFlow(false)
    val sabahoUsedSkip: StateFlow<Boolean> = _sabahoUsedSkip.asStateFlow()

    private val _sabahoUsedExtraTime = MutableStateFlow(false)
    val sabahoUsedExtraTime: StateFlow<Boolean> = _sabahoUsedExtraTime.asStateFlow()

    private val _sabahoSelectedAnswer = MutableStateFlow<Int?>(null)
    val sabahoSelectedAnswer: StateFlow<Int?> = _sabahoSelectedAnswer.asStateFlow()

    private val _sabahoGameOver = MutableStateFlow(false)
    val sabahoGameOver: StateFlow<Boolean> = _sabahoGameOver.asStateFlow()

    private var sabahoTimerJob: Job? = null

    fun startSabahoChallenge() {
        maybeRefreshQuestionFeed()
        val questions = liveSabaho.value.shuffled()
        _sabahoQuestions.value = questions
        _sabahoIndex.value = 0
        _sabahoScore.value = 0
        _sabahoTimer.value = 25
        _sabahoRemovedOptions.value = emptySet()
        _sabahoUsedFiftyFifty.value = false
        _sabahoUsedSkip.value = false
        _sabahoUsedExtraTime.value = false
        _sabahoSelectedAnswer.value = null
        _sabahoGameOver.value = false

        navigateTo(GameScreen.Sabaho)
        audioEffects.playWhistle()
        startSabahoTimer()
    }

    val currentSabahoQuestion: SabahoQuestion?
        get() = _sabahoQuestions.value.getOrNull(_sabahoIndex.value)

    private fun startSabahoTimer() {
        sabahoTimerJob?.cancel()
        sabahoTimerJob = viewModelScope.launch {
            while (_sabahoTimer.value > 0 && !_sabahoGameOver.value) {
                delay(1000)
                _sabahoTimer.value -= 1
                if (_sabahoTimer.value in 1..4) audioEffects.playTick()
            }
            if (_sabahoTimer.value <= 0) {
                audioEffects.playWrong()
                advanceSabahoQuestion(wasCorrect = false)
            }
        }
    }

    fun useFiftyFifty() {
        if (_sabahoUsedFiftyFifty.value) return
        val current = currentSabahoQuestion ?: return
        audioEffects.playClick()
        _sabahoUsedFiftyFifty.value = true

        val wrongIndices = current.options.indices.filter { it != current.correctIndex }.shuffled()
        _sabahoRemovedOptions.value = wrongIndices.take(2).toSet()
    }

    fun useSkipQuestion() {
        if (_sabahoUsedSkip.value) return
        audioEffects.playClick()
        _sabahoUsedSkip.value = true
        advanceSabahoQuestion(wasCorrect = false, isSkip = true)
    }

    fun useExtraTime() {
        if (_sabahoUsedExtraTime.value) return
        audioEffects.playClick()
        _sabahoUsedExtraTime.value = true
        _sabahoTimer.value += 15
    }

    fun answerSabahoQuestion(index: Int) {
        if (_sabahoSelectedAnswer.value != null || _sabahoGameOver.value) return
        val current = currentSabahoQuestion ?: return
        _sabahoSelectedAnswer.value = index

        val isCorrect = index == current.correctIndex
        if (isCorrect) {
            audioEffects.playCorrect()
            _sabahoScore.value += current.difficulty.pointsMultiplier
        } else {
            audioEffects.playWrong()
        }

        viewModelScope.launch {
            delay(1200)
            advanceSabahoQuestion(isCorrect)
        }
    }

    private fun advanceSabahoQuestion(wasCorrect: Boolean, isSkip: Boolean = false) {
        _sabahoSelectedAnswer.value = null
        _sabahoRemovedOptions.value = emptySet()
        if (_sabahoIndex.value + 1 < _sabahoQuestions.value.size) {
            _sabahoIndex.value += 1
            _sabahoTimer.value = 25
            startSabahoTimer()
        } else {
            _sabahoGameOver.value = true
            sabahoTimerJob?.cancel()
            audioEffects.playWhistle()
            val points = _sabahoScore.value * 2
            val coins = (_sabahoScore.value * 0.8).toInt()
            repository.addPointsAndCoins(
                earnedPoints = points,
                earnedCoins = coins,
                gameTitle = "صباحو تحدي"
            )

            if (isOfflineMode.value) {
                viewModelScope.launch {
                    offlineRepository.recordOfflineGamePlayed(
                        gameType = "صباحو تحدي",
                        score = _sabahoScore.value,
                        earnedPoints = points,
                        earnedCoins = coins
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // GAME 3: Mystery Player ("العب مع سري")
    // -------------------------------------------------------------------------
    private val _mysteryPlayers = MutableStateFlow<List<MysteryPlayer>>(emptyList())
    private val _mysteryIndex = MutableStateFlow(0)
    val mysteryIndex: StateFlow<Int> = _mysteryIndex.asStateFlow()

    private val _revealedHintsCount = MutableStateFlow(1) // Starts with 1 hint
    val revealedHintsCount: StateFlow<Int> = _revealedHintsCount.asStateFlow()

    private val _mysteryGuessed = MutableStateFlow(false)
    val mysteryGuessed: StateFlow<Boolean> = _mysteryGuessed.asStateFlow()

    private val _mysteryCorrect = MutableStateFlow(false)
    val mysteryCorrect: StateFlow<Boolean> = _mysteryCorrect.asStateFlow()

    private val _mysteryScore = MutableStateFlow(0)
    val mysteryScore: StateFlow<Int> = _mysteryScore.asStateFlow()

    fun startMysteryPlayerGame() {
        val list = repository.getMysteryPlayers().shuffled()
        _mysteryPlayers.value = list
        _mysteryIndex.value = 0
        _revealedHintsCount.value = 1
        _mysteryGuessed.value = false
        _mysteryCorrect.value = false
        _mysteryScore.value = 0

        navigateTo(GameScreen.MysteryPlayerScreen)
        audioEffects.playWhistle()
    }

    val currentMysteryPlayer: MysteryPlayer?
        get() = _mysteryPlayers.value.getOrNull(_mysteryIndex.value)

    fun revealNextHint() {
        val current = currentMysteryPlayer ?: return
        if (_revealedHintsCount.value < current.hints.size && !_mysteryGuessed.value) {
            audioEffects.playClick()
            _revealedHintsCount.value += 1
        }
    }

    fun guessMysteryPlayer(name: String) {
        if (_mysteryGuessed.value) return
        val current = currentMysteryPlayer ?: return
        _mysteryGuessed.value = true

        val isRight = repository.normalizeArabic(name) == repository.normalizeArabic(current.playerName)
        _mysteryCorrect.value = isRight

        if (isRight) {
            audioEffects.playCorrect()
            // Points decrease with each revealed hint: 100 - (hints - 1)*15
            val points = (100 - (_revealedHintsCount.value - 1) * 15).coerceAtLeast(25)
            _mysteryScore.value += points
            repository.addPointsAndCoins(earnedPoints = points, earnedCoins = points / 2)
        } else {
            audioEffects.playWrong()
        }
    }

    fun nextMysteryPlayer() {
        audioEffects.playClick()
        if (_mysteryIndex.value + 1 < _mysteryPlayers.value.size) {
            _mysteryIndex.value += 1
            _revealedHintsCount.value = 1
            _mysteryGuessed.value = false
            _mysteryCorrect.value = false
        } else {
            navigateBack()
        }
    }

    // -------------------------------------------------------------------------
    // GAME 4: Career Path Challenge ("خمّن اللاعب من مسيرته")
    // -------------------------------------------------------------------------
    private val _careerChallenges = MutableStateFlow<List<CareerChallenge>>(emptyList())
    private val _careerIndex = MutableStateFlow(0)
    val careerIndex: StateFlow<Int> = _careerIndex.asStateFlow()

    private val _careerGuessed = MutableStateFlow(false)
    val careerGuessed: StateFlow<Boolean> = _careerGuessed.asStateFlow()

    private val _careerSelected = MutableStateFlow<String?>(null)
    val careerSelected: StateFlow<String?> = _careerSelected.asStateFlow()

    private val _careerScore = MutableStateFlow(0)
    val careerScore: StateFlow<Int> = _careerScore.asStateFlow()

    fun startCareerChallenge() {
        val list = repository.getCareerChallenges().shuffled()
        _careerChallenges.value = list
        _careerIndex.value = 0
        _careerGuessed.value = false
        _careerSelected.value = null
        _careerScore.value = 0

        navigateTo(GameScreen.CareerPathScreen)
        audioEffects.playWhistle()
    }

    val currentCareerChallenge: CareerChallenge?
        get() = _careerChallenges.value.getOrNull(_careerIndex.value)

    fun answerCareer(chosenName: String) {
        if (_careerGuessed.value) return
        val current = currentCareerChallenge ?: return
        _careerGuessed.value = true
        _careerSelected.value = chosenName

        val isRight = repository.normalizeArabic(chosenName) == repository.normalizeArabic(current.playerName)
        if (isRight) {
            audioEffects.playCorrect()
            _careerScore.value += 50
            repository.addPointsAndCoins(earnedPoints = 50, earnedCoins = 20)
        } else {
            audioEffects.playWrong()
        }
    }

    fun nextCareerChallenge() {
        audioEffects.playClick()
        if (_careerIndex.value + 1 < _careerChallenges.value.size) {
            _careerIndex.value += 1
            _careerGuessed.value = false
            _careerSelected.value = null
        } else {
            navigateBack()
        }
    }

    // -------------------------------------------------------------------------
    // GAME 5: True or False Blitz ("صح أو غلط")
    // -------------------------------------------------------------------------
    private val _tfQuestions = MutableStateFlow<List<TrueFalseQuestion>>(emptyList())
    private val _tfIndex = MutableStateFlow(0)
    val tfIndex: StateFlow<Int> = _tfIndex.asStateFlow()

    private val _tfStreak = MutableStateFlow(0)
    val tfStreak: StateFlow<Int> = _tfStreak.asStateFlow()

    private val _tfScore = MutableStateFlow(0)
    val tfScore: StateFlow<Int> = _tfScore.asStateFlow()

    private val _tfSelected = MutableStateFlow<Boolean?>(null)
    val tfSelected: StateFlow<Boolean?> = _tfSelected.asStateFlow()

    private val _tfExplanationVisible = MutableStateFlow(false)
    val tfExplanationVisible: StateFlow<Boolean> = _tfExplanationVisible.asStateFlow()

    fun startTrueFalseGame() {
        maybeRefreshQuestionFeed()
        val list = liveTrueFalse.value.shuffled()
        _tfQuestions.value = list
        _tfIndex.value = 0
        _tfStreak.value = 0
        _tfScore.value = 0
        _tfSelected.value = null
        _tfExplanationVisible.value = false

        navigateTo(GameScreen.TrueFalseScreen)
        audioEffects.playWhistle()
    }

    val currentTrueFalse: TrueFalseQuestion?
        get() = _tfQuestions.value.getOrNull(_tfIndex.value)

    fun answerTrueFalse(answer: Boolean) {
        if (_tfSelected.value != null) return
        val current = currentTrueFalse ?: return
        _tfSelected.value = answer
        _tfExplanationVisible.value = true

        val isCorrect = answer == current.isTrue
        if (isCorrect) {
            audioEffects.playCorrect()
            val newStreak = _tfStreak.value + 1
            _tfStreak.value = newStreak
            val points = 10 * newStreak // streak multiplier!
            _tfScore.value += points
            repository.addPointsAndCoins(earnedPoints = points, earnedCoins = points / 3)
        } else {
            audioEffects.playWrong()
            _tfStreak.value = 0
        }
    }

    fun nextTrueFalse() {
        audioEffects.playClick()
        _tfSelected.value = null
        _tfExplanationVisible.value = false
        if (_tfIndex.value + 1 < _tfQuestions.value.size) {
            _tfIndex.value += 1
        } else {
            navigateBack()
        }
    }

    // -------------------------------------------------------------------------
    // GAME 6: Event Ordering ("ترتيب الأهداف والأحداث")
    // -------------------------------------------------------------------------
    private val _eventChallenges = MutableStateFlow<List<EventOrderChallenge>>(emptyList())
    private val _eventChallengeIndex = MutableStateFlow(0)
    val eventChallengeIndex: StateFlow<Int> = _eventChallengeIndex.asStateFlow()

    private val _currentOrderItems = MutableStateFlow<List<FootballEventItem>>(emptyList())
    val currentOrderItems: StateFlow<List<FootballEventItem>> = _currentOrderItems.asStateFlow()

    private val _orderValidated = MutableStateFlow(false)
    val orderValidated: StateFlow<Boolean> = _orderValidated.asStateFlow()

    private val _orderIsAllCorrect = MutableStateFlow(false)
    val orderIsAllCorrect: StateFlow<Boolean> = _orderIsAllCorrect.asStateFlow()

    fun startEventOrderingGame() {
        val list = repository.getEventOrderChallenges().shuffled()
        _eventChallenges.value = list
        _eventChallengeIndex.value = 0
        loadEventOrderChallenge(list.firstOrNull())

        navigateTo(GameScreen.EventOrderingScreen)
        audioEffects.playWhistle()
    }

    private fun loadEventOrderChallenge(challenge: EventOrderChallenge?) {
        if (challenge == null) return
        // Shuffle initially so user has to sort
        _currentOrderItems.value = challenge.items.shuffled()
        _orderValidated.value = false
        _orderIsAllCorrect.value = false
    }

    fun moveOrderItem(index: Int, up: Boolean) {
        val list = _currentOrderItems.value.toMutableList()
        val targetIndex = if (up) index - 1 else index + 1
        if (targetIndex in list.indices) {
            audioEffects.playClick()
            val temp = list[index]
            list[index] = list[targetIndex]
            list[targetIndex] = temp
            _currentOrderItems.value = list
            _orderValidated.value = false
        }
    }

    fun validateOrder() {
        val items = _currentOrderItems.value
        val isCorrect = items.map { it.orderRank } == items.map { it.orderRank }.sorted()
        _orderValidated.value = true
        _orderIsAllCorrect.value = isCorrect

        if (isCorrect) {
            audioEffects.playCorrect()
            repository.addPointsAndCoins(earnedPoints = 60, earnedCoins = 30)
        } else {
            audioEffects.playWrong()
        }
    }

    fun nextEventChallenge() {
        audioEffects.playClick()
        if (_eventChallengeIndex.value + 1 < _eventChallenges.value.size) {
            _eventChallengeIndex.value += 1
            loadEventOrderChallenge(_eventChallenges.value[_eventChallengeIndex.value])
        } else {
            navigateBack()
        }
    }

    // -------------------------------------------------------------------------
    // DIFFICULTY SELECTION COMPONENT (سهل / متوسط / صعب)
    // -------------------------------------------------------------------------
    private val _selectedDifficulty = MutableStateFlow<GameDifficulty>(GameDifficulty.MEDIUM)
    val selectedDifficulty: StateFlow<GameDifficulty> = _selectedDifficulty.asStateFlow()

    private val _showDifficultyDialog = MutableStateFlow(false)
    val showDifficultyDialog: StateFlow<Boolean> = _showDifficultyDialog.asStateFlow()

    private val _targetGameForDifficulty = MutableStateFlow<GameScreen?>(null)
    val targetGameForDifficulty: StateFlow<GameScreen?> = _targetGameForDifficulty.asStateFlow()

    fun openDifficultySelector(target: GameScreen) {
        audioEffects.playClick()
        _targetGameForDifficulty.value = target
        _showDifficultyDialog.value = true
    }

    fun dismissDifficultySelector() {
        _showDifficultyDialog.value = false
        _targetGameForDifficulty.value = null
    }

    fun setDifficulty(difficulty: GameDifficulty) {
        audioEffects.playTick()
        _selectedDifficulty.value = difficulty
    }

    fun confirmDifficultyAndStart(difficulty: GameDifficulty) {
        _selectedDifficulty.value = difficulty
        val target = _targetGameForDifficulty.value
        _showDifficultyDialog.value = false
        _targetGameForDifficulty.value = null

        when (target) {
            is GameScreen.ThirtySecondsSelect -> navigateTo(GameScreen.ThirtySecondsSelect)
            is GameScreen.ThirtySecondsQuiz -> startThirtySecondsQuiz()
            is GameScreen.ThirtySecondsPlayers -> startPlayerMentionChallenge()
            is GameScreen.Sabaho -> startSabahoChallenge()
            is GameScreen.MysteryPlayerScreen -> startMysteryPlayerGame()
            is GameScreen.CareerPathScreen -> startCareerChallenge()
            is GameScreen.TrueFalseScreen -> startTrueFalseGame()
            is GameScreen.EventOrderingScreen -> startEventOrderingGame()
            is GameScreen.PvPMatchScreen -> startPvPMatchSearch()
            else -> {}
        }
    }

    // -------------------------------------------------------------------------
    // REAL-TIME PvP MATCH (المواجهة المباشرة)
    // -------------------------------------------------------------------------
    private val _pvpState = MutableStateFlow(
        PvPMatchState(
            user = PvPPlayer("كابتن الملعب", "👑", "ريال مدريد"),
            opponent = PvPPlayer("فارس البرشا", "🔵🔴", "برشلونة")
        )
    )
    val pvpState: StateFlow<PvPMatchState> = _pvpState.asStateFlow()

    private var pvpTimerJob: Job? = null
    private var pvpOpponentJob: Job? = null
    private var pvpQuestions = listOf<PvPQuestion>()

    fun startPvPMatchSearch() {
        audioEffects.playWhistle()
        val user = userProfile.value
        val userPvP = PvPPlayer(
            name = user.playerName,
            avatarEmoji = user.avatarEmoji,
            club = user.favoriteClub,
            score = 0
        )
        val opponent = repository.getRandomOpponent()

        _pvpState.value = PvPMatchState(
            isSearching = true,
            currentRound = 1,
            totalRounds = 5,
            user = userPvP,
            opponent = opponent,
            currentQuestion = null,
            roundTimer = 12,
            commentaryMessage = "جاري البحث عن منافس كروي حقيقي في السيرفر... 🔍"
        )
        navigateTo(GameScreen.PvPMatchScreen)

        viewModelScope.launch {
            delay(2200) // Simulate finding opponent
            maybeRefreshQuestionFeed()
            pvpQuestions = livePvp.value.shuffled().take(5)
            if (pvpQuestions.isNotEmpty()) {
                val q1 = pvpQuestions[0]
                _pvpState.value = _pvpState.value.copy(
                    isSearching = false,
                    currentRound = 1,
                    currentQuestion = q1,
                    userSelectedOption = null,
                    opponentSelectedOption = null,
                    roundTimer = 12,
                    commentaryMessage = "تم العثور على المنافس! انطلقت الجولة 1 ⚽"
                )
                startPvPRoundTimer()
                scheduleOpponentAnswer(q1)
            }
        }
    }

    private fun startPvPRoundTimer() {
        pvpTimerJob?.cancel()
        pvpTimerJob = viewModelScope.launch {
            while (_pvpState.value.roundTimer > 0 && !_pvpState.value.isMatchFinished) {
                delay(1000)
                val newTimer = _pvpState.value.roundTimer - 1
                _pvpState.value = _pvpState.value.copy(roundTimer = newTimer)
                if (newTimer <= 3 && newTimer > 0) {
                    audioEffects.playTick()
                }
            }
            if (!_pvpState.value.isMatchFinished && _pvpState.value.userSelectedOption == null) {
                // Round timed out for user
                onPvPRoundTimeout()
            }
        }
    }

    private fun scheduleOpponentAnswer(question: PvPQuestion) {
        pvpOpponentJob?.cancel()
        pvpOpponentJob = viewModelScope.launch {
            // Opponent answers between 2.5s and 6.5s
            val delayMs = (2500L..6500L).random()
            delay(delayMs)
            if (!_pvpState.value.isMatchFinished) {
                // Opponent accuracy based on difficulty
                val isCorrect = when (_selectedDifficulty.value) {
                    GameDifficulty.EASY -> listOf(true, false).random()
                    GameDifficulty.MEDIUM -> listOf(true, true, false).random()
                    GameDifficulty.HARD -> listOf(true, true, true, false).random()
                }
                val chosenIndex = if (isCorrect) question.correctIndex else (0..3).filter { it != question.correctIndex }.random()
                val newOpponentScore = if (isCorrect) _pvpState.value.opponent.score + 1 else _pvpState.value.opponent.score
                val updatedOpponent = _pvpState.value.opponent.copy(
                    score = newOpponentScore,
                    lastAnswerStatus = isCorrect
                )

                val oppCommentary = if (isCorrect) {
                    "⚽ المنافس (${updatedOpponent.name}) أحرز هدفاً سريعاً!"
                } else {
                    "❌ المنافس (${updatedOpponent.name}) أهدر فرصة مؤكدة!"
                }

                _pvpState.value = _pvpState.value.copy(
                    opponent = updatedOpponent,
                    opponentSelectedOption = chosenIndex,
                    commentaryMessage = oppCommentary
                )

                // If user already answered, finish round
                if (_pvpState.value.userSelectedOption != null) {
                    handleRoundFinishedTransition()
                }
            }
        }
    }

    fun answerPvPQuestion(optionIndex: Int) {
        val current = _pvpState.value
        if (current.userSelectedOption != null || current.isMatchFinished || current.isSearching) return
        val question = current.currentQuestion ?: return

        val isCorrect = optionIndex == question.correctIndex
        val newUserScore = if (isCorrect) current.user.score + 1 else current.user.score
        val updatedUser = current.user.copy(
            score = newUserScore,
            lastAnswerStatus = isCorrect
        )

        if (isCorrect) {
            audioEffects.playCorrect()
        } else {
            audioEffects.playWrong()
        }

        val commentary = if (isCorrect) {
            "⚽ جوووول! إجابة صاروخية من ${updatedUser.name} في شباك المنافس!"
        } else {
            "❌ تصدٍ للحارس! إجابة غير دقيقة من ${updatedUser.name}!"
        }

        _pvpState.value = current.copy(
            user = updatedUser,
            userSelectedOption = optionIndex,
            commentaryMessage = commentary
        )

        // If opponent also answered, proceed
        if (current.opponentSelectedOption != null) {
            handleRoundFinishedTransition()
        }
    }

    private fun onPvPRoundTimeout() {
        val current = _pvpState.value
        audioEffects.playWrong()
        _pvpState.value = current.copy(
            user = current.user.copy(lastAnswerStatus = false),
            userSelectedOption = -1,
            commentaryMessage = "⏱️ انتهى وقت الجولة! لم يسجل ${current.user.name}"
        )
        handleRoundFinishedTransition()
    }

    private fun handleRoundFinishedTransition() {
        pvpTimerJob?.cancel()
        pvpOpponentJob?.cancel()

        viewModelScope.launch {
            delay(2200) // Let users see the round result and live score
            val current = _pvpState.value
            if (current.currentRound < current.totalRounds) {
                val nextRound = current.currentRound + 1
                val nextQuestion = pvpQuestions.getOrNull(nextRound - 1)
                _pvpState.value = current.copy(
                    currentRound = nextRound,
                    currentQuestion = nextQuestion,
                    userSelectedOption = null,
                    opponentSelectedOption = null,
                    roundTimer = 12,
                    commentaryMessage = "صافرة انطلاق الجولة $nextRound! التعليق مشتعل 🔥"
                )
                startPvPRoundTimer()
                if (nextQuestion != null) {
                    scheduleOpponentAnswer(nextQuestion)
                }
            } else {
                // Match Ended!
                finishPvPMatch()
            }
        }
    }

    private fun finishPvPMatch() {
        val current = _pvpState.value
        val userScore = current.user.score
        val oppScore = current.opponent.score

        val won = userScore > oppScore
        val draw = userScore == oppScore

        val earnedPoints = if (won) 120 else if (draw) 50 else 25
        val earnedCoins = if (won) 60 else if (draw) 25 else 10

        val title = if (current.isFriendlyMatch && current.friendlyCode != null) {
            "مواجهة ودية [رمز: ${current.friendlyCode}]"
        } else {
            "المواجهة المباشرة PvP"
        }

        repository.addPointsAndCoins(
            earnedPoints = earnedPoints,
            earnedCoins = earnedCoins,
            gameTitle = title
        )

        // Save friendly match history
        if (current.isFriendlyMatch && current.friendlyCode != null) {
            val newHistoryItem = FriendlyMatchHistoryItem(
                id = "fm_${System.currentTimeMillis()}",
                friendName = current.opponent.name,
                friendAvatar = current.opponent.avatarEmoji,
                userScore = userScore,
                friendScore = oppScore,
                isWin = won,
                challengeCode = current.friendlyCode,
                timeAgo = "الآن"
            )
            _friendlyMatchHistory.value = listOf(newHistoryItem) + _friendlyMatchHistory.value
        }

        // Record to local Room database if offline mode
        if (isOfflineMode.value) {
            viewModelScope.launch {
                offlineRepository.recordOfflineGamePlayed(
                    gameType = title,
                    score = userScore,
                    earnedPoints = earnedPoints,
                    earnedCoins = earnedCoins
                )
            }
        }

        if (won) {
            audioEffects.playWhistle()
        } else {
            audioEffects.playWrong()
        }

        val finalCommentary = when {
            won -> if (current.isFriendlyMatch) "🏆 فوز ساحق لـ ${current.user.name} على صديقه ${current.opponent.name}!" else "🏆 فوز تاريخي ساحق لـ ${current.user.name} في كلاسيكو المواجهة المباشرة!"
            draw -> "🤝 مباراة ملحمية تنتهي بتعادل الفريقين واقتسام النقاط!"
            else -> if (current.isFriendlyMatch) "💔 تفوق الصديق ${current.opponent.name} بفارق ضئيل، حظاً أوفر في الجولة القادمة!" else "💔 خسر كابتن الملعب بفارق ضئيل، حظاً أوفر في المواجهة القادمة!"
        }

        _pvpState.value = current.copy(
            isMatchFinished = true,
            commentaryMessage = finalCommentary
        )
    }

    fun startFriendlyMatch(code: String, customOpponent: FriendlyChallengeInfo? = null) {
        audioEffects.playWhistle()
        val user = userProfile.value
        val userPvP = PvPPlayer(
            name = user.playerName,
            avatarEmoji = user.avatarEmoji,
            club = user.favoriteClub,
            score = 0
        )
        val cleanCode = code.trim().uppercase()
        val opponentInfo = customOpponent
            ?: _friendlyFriendsList.value.firstOrNull { it.code.equals(cleanCode, ignoreCase = true) }
            ?: FriendlyChallengeInfo(
                code = cleanCode,
                friendName = "صديق التحدي ($cleanCode)",
                friendAvatar = "🤝",
                friendClub = "فريق الأصدقاء",
                difficulty = _selectedDifficulty.value
            )

        val oppPvP = PvPPlayer(
            name = opponentInfo.friendName,
            avatarEmoji = opponentInfo.friendAvatar,
            club = opponentInfo.friendClub,
            score = 0
        )

        _pvpState.value = PvPMatchState(
            isSearching = true,
            currentRound = 1,
            totalRounds = 5,
            user = userPvP,
            opponent = oppPvP,
            currentQuestion = null,
            roundTimer = 12,
            commentaryMessage = "جاري الاتصال بغرفة المواجهة الودية برمز [$cleanCode]... 🤝",
            isFriendlyMatch = true,
            friendlyCode = cleanCode
        )
        navigateTo(GameScreen.PvPMatchScreen)

        viewModelScope.launch {
            delay(1800) // Simulated room join
            maybeRefreshQuestionFeed()
            pvpQuestions = livePvp.value.shuffled().take(5)
            if (pvpQuestions.isNotEmpty()) {
                val q1 = pvpQuestions[0]
                _pvpState.value = _pvpState.value.copy(
                    isSearching = false,
                    currentRound = 1,
                    currentQuestion = q1,
                    userSelectedOption = null,
                    opponentSelectedOption = null,
                    roundTimer = 12,
                    commentaryMessage = "صافرة البداية! انطلقت المواجهة الودية ضد ${oppPvP.name} ⚽"
                )
                startPvPRoundTimer()
                scheduleOpponentAnswer(q1)
            }
        }
    }

    fun rematchPvP() {
        val current = _pvpState.value
        if (current.isFriendlyMatch && current.friendlyCode != null) {
            startFriendlyMatch(current.friendlyCode)
        } else {
            startPvPMatchSearch()
        }
    }

    // =========================================================================
    // 1. AUTOBUS COMPLETE (أتوبيس كومبليت)
    // =========================================================================
    private var autobusTimerJob: Job? = null
    private var autobusAiJob: Job? = null

    private val _autobusLetter = MutableStateFlow(PartyGamesRepository.getRandomLetter())
    val autobusLetter: StateFlow<AutobusLetterData> = _autobusLetter.asStateFlow()

    private val _autobusUserAnswers = MutableStateFlow(AutobusUserAnswers())
    val autobusUserAnswers: StateFlow<AutobusUserAnswers> = _autobusUserAnswers.asStateFlow()

    private val _autobusTimeLeft = MutableStateFlow(60)
    val autobusTimeLeft: StateFlow<Int> = _autobusTimeLeft.asStateFlow()

    private val _autobusIsRunning = MutableStateFlow(false)
    val autobusIsRunning: StateFlow<Boolean> = _autobusIsRunning.asStateFlow()

    private val _autobusIsFinished = MutableStateFlow(false)
    val autobusIsFinished: StateFlow<Boolean> = _autobusIsFinished.asStateFlow()

    private val _autobusRoundResult = MutableStateFlow<AutobusRoundResult?>(null)
    val autobusRoundResult: StateFlow<AutobusRoundResult?> = _autobusRoundResult.asStateFlow()

    private val _autobusAiProgress = MutableStateFlow(0)
    val autobusAiProgress: StateFlow<Int> = _autobusAiProgress.asStateFlow()

    private val _autobusTotalScore = MutableStateFlow(0)
    val autobusTotalScore: StateFlow<Int> = _autobusTotalScore.asStateFlow()

    private val _autobusRoundsCount = MutableStateFlow(1)
    val autobusRoundsCount: StateFlow<Int> = _autobusRoundsCount.asStateFlow()

    fun startAutobusGame(letter: Char? = null) {
        audioEffects.playWhistle()
        autobusTimerJob?.cancel()
        autobusAiJob?.cancel()

        val chosenLetter = if (letter != null) {
            PartyGamesRepository.getLetterData(letter)
        } else {
            PartyGamesRepository.getRandomLetter()
        }

        _autobusLetter.value = chosenLetter
        _autobusUserAnswers.value = AutobusUserAnswers()
        _autobusTimeLeft.value = 60
        _autobusIsRunning.value = true
        _autobusIsFinished.value = false
        _autobusRoundResult.value = null
        _autobusAiProgress.value = 0

        navigateTo(GameScreen.AutobusCompleteScreen)

        autobusTimerJob = viewModelScope.launch {
            while (_autobusTimeLeft.value > 0 && _autobusIsRunning.value) {
                delay(1000)
                if (_autobusIsRunning.value) {
                    _autobusTimeLeft.value -= 1
                    if (_autobusTimeLeft.value in 1..10) {
                        audioEffects.playTick()
                    }
                }
            }
            if (_autobusIsRunning.value && _autobusTimeLeft.value <= 0) {
                finishAutobusRound(isTimeUp = true)
            }
        }

        autobusAiJob = viewModelScope.launch {
            for (i in 1..8) {
                delay((4000..7000).random().toLong())
                if (!_autobusIsRunning.value) break
                _autobusAiProgress.value = i
                if (i == 8 && (1..3).random() == 1) {
                    delay(3000)
                    if (_autobusIsRunning.value) {
                        finishAutobusRound(isAiCompletedFirst = true)
                        break
                    }
                }
            }
        }
    }

    fun updateAutobusAnswer(type: AutobusFieldType, text: String) {
        val current = _autobusUserAnswers.value
        val updated = current.copy()
        updated.setAnswer(type, text)
        _autobusUserAnswers.value = updated
    }

    fun claimAutobusComplete() {
        if (!_autobusIsRunning.value) return
        audioEffects.playWhistle()
        finishAutobusRound(isPlayerCompletedFirst = true)
    }

    private fun finishAutobusRound(
        isTimeUp: Boolean = false,
        isPlayerCompletedFirst: Boolean = false,
        isAiCompletedFirst: Boolean = false
    ) {
        _autobusIsRunning.value = false
        autobusTimerJob?.cancel()
        autobusAiJob?.cancel()

        val letterData = _autobusLetter.value
        val answers = _autobusUserAnswers.value
        val results = mutableListOf<AutobusFieldResult>()

        val aiAnswers = listOf(
            letterData.sampleBoy,
            letterData.sampleGirl,
            letterData.sampleAnimal,
            letterData.samplePlant,
            letterData.sampleObject,
            letterData.sampleCountry,
            letterData.sampleJob,
            letterData.sampleFood
        )

        val categories = AutobusFieldType.values()
        var roundScore = 0
        var aiScore = 0

        categories.forEachIndexed { index, cat ->
            val userAns = answers.getAnswer(cat)
            val aiAns = aiAnswers.getOrElse(index) { "" }
            val evaluated = PartyGamesRepository.evaluateAutobusAnswer(
                letter = letterData.letter,
                category = cat,
                userAnswerRaw = userAns,
                aiAnswer = aiAns
            )
            results.add(evaluated)
            roundScore += evaluated.scoreAwarded

            val aiFilled = index < _autobusAiProgress.value || isAiCompletedFirst
            if (aiFilled) {
                aiScore += if (evaluated.scoreAwarded == 5) 5 else 10
            }
        }

        if (isPlayerCompletedFirst) {
            roundScore += 10
        }

        _autobusTotalScore.value += roundScore
        val timeTaken = 60 - _autobusTimeLeft.value
        _autobusRoundResult.value = AutobusRoundResult(
            letter = letterData.letter,
            totalScore = roundScore,
            aiTotalScore = aiScore,
            fieldResults = results,
            timeTakenSeconds = timeTaken
        )
        _autobusIsFinished.value = true

        if (roundScore > aiScore) {
            audioEffects.playVictory()
            repository.addPointsAndCoins(roundScore * 5, roundScore / 2)
        } else {
            audioEffects.playCorrect()
            repository.addPointsAndCoins(roundScore * 3, roundScore / 3)
        }
        recordPartyGameScore("أتوبيس كومبليت", "party", roundScore, "حرف ${letterData.letter} • نتيجة: $roundScore نقطة 🚌")

        if (isOfflineMode.value) {
            viewModelScope.launch {
                offlineRepository.recordOfflineGamePlayed(
                    gameType = "أتوبيس كومبليت (${letterData.letter})",
                    score = roundScore,
                    earnedPoints = roundScore * 5,
                    earnedCoins = roundScore / 2
                )
            }
        }
    }

    fun nextAutobusRound() {
        _autobusRoundsCount.value += 1
        startAutobusGame()
    }

    // =========================================================================
    // 2. FIVE SECONDS CHALLENGE (تحدي الـ 5 ثواني)
    // =========================================================================
    private var fiveSecondsTimerJob: Job? = null

    private val _fiveSecondsList = MutableStateFlow(PartyGamesRepository.fiveSecondsPrompts.shuffled())
    private val _fiveSecondsIndex = MutableStateFlow(0)
    private val _fiveSecondsPromptState = MutableStateFlow(PartyGamesRepository.fiveSecondsPrompts[0])
    val fiveSecondsPromptState: StateFlow<FiveSecondsPrompt> = _fiveSecondsPromptState.asStateFlow()

    private val _fiveSecondsTimeLeft = MutableStateFlow(5.0f)
    val fiveSecondsTimeLeft: StateFlow<Float> = _fiveSecondsTimeLeft.asStateFlow()

    private val _fiveSecondsCount = MutableStateFlow(0)
    val fiveSecondsCount: StateFlow<Int> = _fiveSecondsCount.asStateFlow()

    private val _fiveSecondsScore = MutableStateFlow(0)
    val fiveSecondsScore: StateFlow<Int> = _fiveSecondsScore.asStateFlow()

    private val _fiveSecondsIsRunning = MutableStateFlow(false)
    val fiveSecondsIsRunning: StateFlow<Boolean> = _fiveSecondsIsRunning.asStateFlow()

    private val _fiveSecondsRoundFinished = MutableStateFlow(false)
    val fiveSecondsRoundFinished: StateFlow<Boolean> = _fiveSecondsRoundFinished.asStateFlow()

    private val _fiveSecondsSuccess = MutableStateFlow(false)
    val fiveSecondsSuccess: StateFlow<Boolean> = _fiveSecondsSuccess.asStateFlow()

    fun startFiveSecondsGame() {
        audioEffects.playClick()
        val list = PartyGamesRepository.fiveSecondsPrompts.shuffled()
        _fiveSecondsList.value = list
        _fiveSecondsIndex.value = 0
        _fiveSecondsScore.value = 0
        _fiveSecondsPromptState.value = list[0]
        _fiveSecondsCount.value = 0
        _fiveSecondsTimeLeft.value = 5.0f
        _fiveSecondsIsRunning.value = false
        _fiveSecondsRoundFinished.value = false
        _fiveSecondsSuccess.value = false
        navigateTo(GameScreen.FiveSecondsScreen)
    }

    fun startFiveSecondsCountdown() {
        if (_fiveSecondsIsRunning.value) return
        audioEffects.playWhistle()
        _fiveSecondsCount.value = 0
        _fiveSecondsTimeLeft.value = 5.0f
        _fiveSecondsIsRunning.value = true
        _fiveSecondsRoundFinished.value = false
        _fiveSecondsSuccess.value = false

        fiveSecondsTimerJob?.cancel()
        fiveSecondsTimerJob = viewModelScope.launch {
            val totalSteps = 50
            for (step in 1..totalSteps) {
                delay(100)
                if (!_fiveSecondsIsRunning.value) break
                val remaining = 5.0f - (step * 0.1f)
                _fiveSecondsTimeLeft.value = maxOf(0.0f, remaining)
                if (remaining in 0.1f..2.0f && step % 3 == 0) {
                    audioEffects.playTick()
                }
            }
            if (_fiveSecondsIsRunning.value) {
                _fiveSecondsIsRunning.value = false
                _fiveSecondsRoundFinished.value = true
                val success = _fiveSecondsCount.value >= 3
                _fiveSecondsSuccess.value = success
                if (success) {
                    audioEffects.playVictory()
                    _fiveSecondsScore.value += 1
                    repository.addPointsAndCoins(30, 10)
                } else {
                    audioEffects.playWrong()
                }
            }
        }
    }

    fun tapFiveSecondsCount() {
        if (!_fiveSecondsIsRunning.value) return
        val next = _fiveSecondsCount.value + 1
        _fiveSecondsCount.value = next
        audioEffects.playClick()
        if (next >= 3) {
            _fiveSecondsIsRunning.value = false
            fiveSecondsTimerJob?.cancel()
            _fiveSecondsRoundFinished.value = true
            _fiveSecondsSuccess.value = true
            _fiveSecondsScore.value += 1
            audioEffects.playVictory()
            repository.addPointsAndCoins(30, 10)
            recordPartyGameScore("تحدي الـ 5 ثواني", "party", 30, "3 إجابات في أقل من 5 ثوانٍ ⚡")

            if (isOfflineMode.value) {
                viewModelScope.launch {
                    offlineRepository.recordOfflineGamePlayed(
                        gameType = "تحدي الـ 5 ثواني",
                        score = _fiveSecondsScore.value,
                        earnedPoints = 30,
                        earnedCoins = 10
                    )
                }
            }
        }
    }

    fun nextFiveSecondsPrompt() {
        fiveSecondsTimerJob?.cancel()
        val list = _fiveSecondsList.value
        val nextIndex = (_fiveSecondsIndex.value + 1) % list.size
        _fiveSecondsIndex.value = nextIndex
        _fiveSecondsPromptState.value = list[nextIndex]
        _fiveSecondsCount.value = 0
        _fiveSecondsTimeLeft.value = 5.0f
        _fiveSecondsIsRunning.value = false
        _fiveSecondsRoundFinished.value = false
        _fiveSecondsSuccess.value = false
    }

    // =========================================================================
    // 3. WOULD YOU RATHER (لو خيروك؟)
    // =========================================================================
    private val _wyrList = MutableStateFlow(PartyGamesRepository.wouldYouRatherQuestions.shuffled())
    private val _wyrIndex = MutableStateFlow(0)
    private val _wyrPromptState = MutableStateFlow(PartyGamesRepository.wouldYouRatherQuestions[0])
    val wouldYouRatherPromptState: StateFlow<WouldYouRatherQuestion> = _wyrPromptState.asStateFlow()

    private val _wyrSelectedOption = MutableStateFlow<String?>(null)
    val wouldYouRatherSelectedOption: StateFlow<String?> = _wyrSelectedOption.asStateFlow()

    private val _wyrTotalAnswered = MutableStateFlow(0)
    val wouldYouRatherTotalAnswered: StateFlow<Int> = _wyrTotalAnswered.asStateFlow()

    fun startWouldYouRatherGame() {
        audioEffects.playClick()
        val list = PartyGamesRepository.wouldYouRatherQuestions.shuffled()
        _wyrList.value = list
        _wyrIndex.value = 0
        _wyrPromptState.value = list[0]
        _wyrSelectedOption.value = null
        navigateTo(GameScreen.WouldYouRatherScreen)
    }

    fun selectWouldYouRatherOption(option: String) {
        if (_wyrSelectedOption.value != null) return
        _wyrSelectedOption.value = option
        _wyrTotalAnswered.value += 1
        audioEffects.playCorrect()
        repository.addPointsAndCoins(15, 5)
    }

    fun nextWouldYouRatherQuestion() {
        audioEffects.playClick()
        val list = _wyrList.value
        val nextIndex = (_wyrIndex.value + 1) % list.size
        _wyrIndex.value = nextIndex
        _wyrPromptState.value = list[nextIndex]
        _wyrSelectedOption.value = null
    }

    // =========================================================================
    // 4. GENERAL RIDDLES (فوازير وألغاز الذكاء)
    // =========================================================================
    private val _riddleList = MutableStateFlow(PartyGamesRepository.generalRiddles.shuffled())
    private val _riddleIndex = MutableStateFlow(0)
    private val _riddlePromptState = MutableStateFlow(PartyGamesRepository.generalRiddles[0])
    val generalRiddlePromptState: StateFlow<GeneralRiddle> = _riddlePromptState.asStateFlow()

    private val _riddleRevealedHints = MutableStateFlow(0)
    val riddleRevealedHints: StateFlow<Int> = _riddleRevealedHints.asStateFlow()

    private val _riddleUserGuess = MutableStateFlow("")
    val riddleUserGuess: StateFlow<String> = _riddleUserGuess.asStateFlow()

    private val _riddleIsSolved = MutableStateFlow(false)
    val riddleIsSolved: StateFlow<Boolean> = _riddleIsSolved.asStateFlow()

    private val _riddleFeedback = MutableStateFlow<String?>(null)
    val riddleFeedback: StateFlow<String?> = _riddleFeedback.asStateFlow()

    private val _riddleScore = MutableStateFlow(0)
    val riddleScore: StateFlow<Int> = _riddleScore.asStateFlow()

    fun startGeneralRiddlesGame() {
        audioEffects.playClick()
        val list = PartyGamesRepository.generalRiddles.shuffled()
        _riddleList.value = list
        _riddleIndex.value = 0
        _riddlePromptState.value = list[0]
        _riddleRevealedHints.value = 0
        _riddleUserGuess.value = ""
        _riddleIsSolved.value = false
        _riddleFeedback.value = null
        _riddleScore.value = 0
        navigateTo(GameScreen.GeneralRiddlesScreen)
    }

    fun revealRiddleHint() {
        val current = _riddleRevealedHints.value
        val maxHints = _riddlePromptState.value.hints.size
        if (current < maxHints) {
            audioEffects.playClick()
            _riddleRevealedHints.value = current + 1
        }
    }

    fun setRiddleGuess(guess: String) {
        _riddleUserGuess.value = guess
    }

    fun submitRiddleGuess() {
        val guess = _riddleUserGuess.value.trim()
        if (guess.isEmpty()) return
        val current = _riddlePromptState.value

        val isCorrect = current.acceptableAnswers.any { it.equals(guess, ignoreCase = true) } ||
                guess.contains(current.answer, ignoreCase = true)

        if (isCorrect) {
            _riddleIsSolved.value = true
            _riddleFeedback.value = "إجابة عبقرية! 🎉 ${current.funExplanation}"
            val bonus = maxOf(10, 40 - (_riddleRevealedHints.value * 10))
            _riddleScore.value += bonus
            audioEffects.playVictory()
            repository.addPointsAndCoins(bonus, bonus / 2)

            if (isOfflineMode.value) {
                viewModelScope.launch {
                    offlineRepository.recordOfflineGamePlayed(
                        gameType = "فوازير الذكاء (${current.title})",
                        score = _riddleScore.value,
                        earnedPoints = bonus,
                        earnedCoins = bonus / 2
                    )
                }
            }
        } else {
            audioEffects.playWrong()
            _riddleFeedback.value = "حاول مرة أخرى! 🤔 استخدم وسيلة مساعدة (تلميح) إذا احتجت"
        }
    }

    fun giveUpAndShowRiddleAnswer() {
        val current = _riddlePromptState.value
        _riddleIsSolved.value = true
        _riddleFeedback.value = "الحل الصحيح هو: ${current.answer} 💡 ${current.funExplanation}"
        audioEffects.playWhistle()
    }

    fun nextRiddle() {
        audioEffects.playClick()
        val list = _riddleList.value
        val nextIndex = (_riddleIndex.value + 1) % list.size
        _riddleIndex.value = nextIndex
        _riddlePromptState.value = list[nextIndex]
        _riddleRevealedHints.value = 0
        _riddleUserGuess.value = ""
        _riddleIsSolved.value = false
        _riddleFeedback.value = null
    }

    // =========================================================================
    // 5. بدون كلام (CHARADES & EMOJI MOVIES / PROVERBS)
    // =========================================================================
    private var bedoonKalamTimerJob: Job? = null

    private val _bedoonKalamMode = MutableStateFlow(BedoonKalamMode.EMOJI_GUESS)
    val bedoonKalamMode: StateFlow<BedoonKalamMode> = _bedoonKalamMode.asStateFlow()

    private val _bedoonKalamList = MutableStateFlow(ArabicGatheringGamesRepository.bedoonKalamItems.shuffled())
    private val _bedoonKalamIndex = MutableStateFlow(0)
    private val _bedoonKalamCurrent = MutableStateFlow(ArabicGatheringGamesRepository.bedoonKalamItems[0])
    val bedoonKalamCurrent: StateFlow<BedoonKalamItem> = _bedoonKalamCurrent.asStateFlow()

    private val _bedoonKalamTimeLeft = MutableStateFlow(60)
    val bedoonKalamTimeLeft: StateFlow<Int> = _bedoonKalamTimeLeft.asStateFlow()

    private val _bedoonKalamIsCardRevealed = MutableStateFlow(false)
    val bedoonKalamIsCardRevealed: StateFlow<Boolean> = _bedoonKalamIsCardRevealed.asStateFlow()

    private val _bedoonKalamRevealedHints = MutableStateFlow(0)
    val bedoonKalamRevealedHints: StateFlow<Int> = _bedoonKalamRevealedHints.asStateFlow()

    private val _bedoonKalamScore = MutableStateFlow(0)
    val bedoonKalamScore: StateFlow<Int> = _bedoonKalamScore.asStateFlow()

    private val _bedoonKalamSolved = MutableStateFlow(false)
    val bedoonKalamSolved: StateFlow<Boolean> = _bedoonKalamSolved.asStateFlow()

    private val _bedoonKalamFeedback = MutableStateFlow<String?>(null)
    val bedoonKalamFeedback: StateFlow<String?> = _bedoonKalamFeedback.asStateFlow()

    fun startBedoonKalam(mode: BedoonKalamMode = BedoonKalamMode.EMOJI_GUESS) {
        audioEffects.playClick()
        bedoonKalamTimerJob?.cancel()
        val list = ArabicGatheringGamesRepository.bedoonKalamItems.shuffled()
        _bedoonKalamMode.value = mode
        _bedoonKalamList.value = list
        _bedoonKalamIndex.value = 0
        _bedoonKalamCurrent.value = list[0]
        _bedoonKalamTimeLeft.value = if (mode == BedoonKalamMode.ACTING_CHARADES) 90 else 60
        _bedoonKalamIsCardRevealed.value = false
        _bedoonKalamRevealedHints.value = 0
        _bedoonKalamScore.value = 0
        _bedoonKalamSolved.value = false
        _bedoonKalamFeedback.value = null
        navigateTo(GameScreen.BedoonKalamScreen)
        startBedoonKalamTimer()
    }

    private fun startBedoonKalamTimer() {
        bedoonKalamTimerJob?.cancel()
        bedoonKalamTimerJob = viewModelScope.launch {
            while (_bedoonKalamTimeLeft.value > 0 && !_bedoonKalamSolved.value) {
                delay(1000)
                _bedoonKalamTimeLeft.value -= 1
                if (_bedoonKalamTimeLeft.value in 1..10) {
                    audioEffects.playTick()
                }
            }
            if (_bedoonKalamTimeLeft.value <= 0 && !_bedoonKalamSolved.value) {
                audioEffects.playWrong()
                _bedoonKalamFeedback.value = "انتهى الوقت! الإجابة كانت: ${_bedoonKalamCurrent.value.title}"
            }
        }
    }

    fun revealBedoonKalamActingCard() {
        audioEffects.playClick()
        _bedoonKalamIsCardRevealed.value = true
    }

    fun revealBedoonKalamHint() {
        val current = _bedoonKalamCurrent.value
        if (_bedoonKalamRevealedHints.value < current.hints.size) {
            audioEffects.playClick()
            _bedoonKalamRevealedHints.value += 1
        }
    }

    fun submitBedoonKalamAnswer(guess: String) {
        val trimmed = guess.trim()
        if (trimmed.isEmpty()) return
        val current = _bedoonKalamCurrent.value

        val isCorrect = current.acceptableAnswers.any { it.equals(trimmed, ignoreCase = true) } ||
                trimmed.contains(current.title, ignoreCase = true)

        if (isCorrect) {
            _bedoonKalamSolved.value = true
            bedoonKalamTimerJob?.cancel()
            _bedoonKalamScore.value += 25
            audioEffects.playVictory()
            repository.addPointsAndCoins(25, 10)
            _bedoonKalamFeedback.value = "إجابة صحيحة وعبقرية! 🎉 (${current.title})"
            recordPartyGameScore("بدون كلام", "party", 25, "تخمين ${current.category} (${current.title}) 🎭")

            if (isOfflineMode.value) {
                viewModelScope.launch {
                    offlineRepository.recordOfflineGamePlayed(
                        gameType = "بدون كلام (${current.category})",
                        score = _bedoonKalamScore.value,
                        earnedPoints = 25,
                        earnedCoins = 10
                    )
                }
            }
        } else {
            audioEffects.playWrong()
            _bedoonKalamFeedback.value = "إجابة غير صحيحة، حاول مجدداً! 🤔"
        }
    }

    fun markBedoonKalamActingSuccess() {
        _bedoonKalamSolved.value = true
        bedoonKalamTimerJob?.cancel()
        _bedoonKalamScore.value += 20
        audioEffects.playVictory()
        repository.addPointsAndCoins(20, 10)
        _bedoonKalamFeedback.value = "تمثيل أسطوري! تم تخمين البطاقة بنجاح 🎭"
        recordPartyGameScore("بدون كلام", "party", 20, "تمثيل بطاقة ${_bedoonKalamCurrent.value.title} 🎭")
    }

    fun nextBedoonKalam() {
        audioEffects.playClick()
        bedoonKalamTimerJob?.cancel()
        val list = _bedoonKalamList.value
        val nextIdx = (_bedoonKalamIndex.value + 1) % list.size
        _bedoonKalamIndex.value = nextIdx
        _bedoonKalamCurrent.value = list[nextIdx]
        _bedoonKalamTimeLeft.value = if (_bedoonKalamMode.value == BedoonKalamMode.ACTING_CHARADES) 90 else 60
        _bedoonKalamIsCardRevealed.value = false
        _bedoonKalamRevealedHints.value = 0
        _bedoonKalamSolved.value = false
        _bedoonKalamFeedback.value = null
        startBedoonKalamTimer()
    }

    // =========================================================================
    // 6. التابو العربي (THE FORBIDDEN WORD / TABOO)
    // =========================================================================
    private var tabooTimerJob: Job? = null

    private val _tabooList = MutableStateFlow(ArabicGatheringGamesRepository.tabooCards.shuffled())
    private val _tabooIndex = MutableStateFlow(0)
    private val _tabooCurrent = MutableStateFlow(ArabicGatheringGamesRepository.tabooCards[0])
    val tabooCurrent: StateFlow<TabooCard> = _tabooCurrent.asStateFlow()

    private val _tabooTimeLeft = MutableStateFlow(60)
    val tabooTimeLeft: StateFlow<Int> = _tabooTimeLeft.asStateFlow()

    private val _tabooScore = MutableStateFlow(0)
    val tabooScore: StateFlow<Int> = _tabooScore.asStateFlow()

    private val _tabooPenalties = MutableStateFlow(0)
    val tabooPenalties: StateFlow<Int> = _tabooPenalties.asStateFlow()

    private val _tabooIsRunning = MutableStateFlow(false)
    val tabooIsRunning: StateFlow<Boolean> = _tabooIsRunning.asStateFlow()

    fun startTabooGame() {
        audioEffects.playClick()
        tabooTimerJob?.cancel()
        val list = ArabicGatheringGamesRepository.tabooCards.shuffled()
        _tabooList.value = list
        _tabooIndex.value = 0
        _tabooCurrent.value = list[0]
        _tabooScore.value = 0
        _tabooPenalties.value = 0
        _tabooTimeLeft.value = 60
        _tabooIsRunning.value = false
        navigateTo(GameScreen.TabooScreen)
    }

    fun startTabooTimer() {
        if (_tabooIsRunning.value) return
        audioEffects.playWhistle()
        _tabooIsRunning.value = true
        _tabooTimeLeft.value = 60

        tabooTimerJob?.cancel()
        tabooTimerJob = viewModelScope.launch {
            while (_tabooTimeLeft.value > 0 && _tabooIsRunning.value) {
                delay(1000)
                _tabooTimeLeft.value -= 1
                if (_tabooTimeLeft.value in 1..10) {
                    audioEffects.playTick()
                }
            }
            if (_tabooTimeLeft.value <= 0) {
                _tabooIsRunning.value = false
                audioEffects.playWhistle()
            }
        }
    }

    fun tabooSuccess() {
        if (!_tabooIsRunning.value) return
        audioEffects.playCorrect()
        _tabooScore.value += 1
        repository.addPointsAndCoins(15, 5)
        recordPartyGameScore("التابو العربي", "party", 15, "شرح كلمة ${_tabooCurrent.value.mainWord} 🚫")
        nextTabooCard()
    }

    fun tabooForbiddenWordPenalty() {
        if (!_tabooIsRunning.value) return
        audioEffects.playWrong()
        _tabooPenalties.value += 1
        if (_tabooScore.value > 0) _tabooScore.value -= 1
        nextTabooCard()
    }

    fun tabooSkip() {
        audioEffects.playClick()
        nextTabooCard()
    }

    private fun nextTabooCard() {
        val list = _tabooList.value
        val nextIdx = (_tabooIndex.value + 1) % list.size
        _tabooIndex.value = nextIdx
        _tabooCurrent.value = list[nextIdx]
    }

    // =========================================================================
    // 7. كمّل المثل الشعبي (PROVERB COMPLETION)
    // =========================================================================
    private val _proverbList = MutableStateFlow(ArabicGatheringGamesRepository.proverbItems.shuffled())
    private val _proverbIndex = MutableStateFlow(0)
    private val _proverbCurrent = MutableStateFlow(ArabicGatheringGamesRepository.proverbItems[0])
    val proverbCurrent: StateFlow<ProverbItem> = _proverbCurrent.asStateFlow()

    private val _proverbStreak = MutableStateFlow(0)
    val proverbStreak: StateFlow<Int> = _proverbStreak.asStateFlow()

    private val _proverbScore = MutableStateFlow(0)
    val proverbScore: StateFlow<Int> = _proverbScore.asStateFlow()

    private val _selectedProverbOption = MutableStateFlow<String?>(null)
    val selectedProverbOption: StateFlow<String?> = _selectedProverbOption.asStateFlow()

    private val _proverbIsCorrect = MutableStateFlow<Boolean?>(null)
    val proverbIsCorrect: StateFlow<Boolean?> = _proverbIsCorrect.asStateFlow()

    fun startProverbsGame() {
        audioEffects.playClick()
        val list = ArabicGatheringGamesRepository.proverbItems.shuffled()
        _proverbList.value = list
        _proverbIndex.value = 0
        _proverbCurrent.value = list[0]
        _proverbStreak.value = 0
        _proverbScore.value = 0
        _selectedProverbOption.value = null
        _proverbIsCorrect.value = null
        navigateTo(GameScreen.ProverbsScreen)
    }

    fun selectProverbAnswer(option: String) {
        if (_selectedProverbOption.value != null) return
        _selectedProverbOption.value = option
        val isCorrect = option == _proverbCurrent.value.correctAnswer
        _proverbIsCorrect.value = isCorrect

        if (isCorrect) {
            audioEffects.playCorrect()
            val newStreak = _proverbStreak.value + 1
            _proverbStreak.value = newStreak
            val pts = 15 * newStreak
            _proverbScore.value += pts
            repository.addPointsAndCoins(pts, pts / 3)
            recordPartyGameScore("كمّل المثل الشعبي", "party", pts, "سلسلة $newStreak أمثال متتالية 📜")
        } else {
            audioEffects.playWrong()
            _proverbStreak.value = 0
        }
    }

    fun nextProverb() {
        audioEffects.playClick()
        val list = _proverbList.value
        val nextIdx = (_proverbIndex.value + 1) % list.size
        _proverbIndex.value = nextIdx
        _proverbCurrent.value = list[nextIdx]
        _selectedProverbOption.value = null
        _proverbIsCorrect.value = null
    }

    // =========================================================================
    // 8. المليون العربي (WHO WANTS TO BE A MILLIONAIRE)
    // =========================================================================
    private val _millionaireList = MutableStateFlow(ArabicGatheringGamesRepository.millionaireQuestions)
    private val _millionaireLevel = MutableStateFlow(1) // 1..15
    val millionaireLevel: StateFlow<Int> = _millionaireLevel.asStateFlow()

    private val _millionaireQuestion = MutableStateFlow(ArabicGatheringGamesRepository.millionaireQuestions[0])
    val millionaireQuestion: StateFlow<MillionaireQuestion> = _millionaireQuestion.asStateFlow()

    private val _millionaireSelectedOption = MutableStateFlow<Int?>(null)
    val millionaireSelectedOption: StateFlow<Int?> = _millionaireSelectedOption.asStateFlow()

    private val _millionaireIsCorrect = MutableStateFlow<Boolean?>(null)
    val millionaireIsCorrect: StateFlow<Boolean?> = _millionaireIsCorrect.asStateFlow()

    private val _millionaireFiftyFiftyUsed = MutableStateFlow(false)
    val millionaireFiftyFiftyUsed: StateFlow<Boolean> = _millionaireFiftyFiftyUsed.asStateFlow()

    private val _millionaireEliminatedIndices = MutableStateFlow<List<Int>>(emptyList())
    val millionaireEliminatedIndices: StateFlow<List<Int>> = _millionaireEliminatedIndices.asStateFlow()

    private val _millionaireAudienceUsed = MutableStateFlow(false)
    val millionaireAudienceUsed: StateFlow<Boolean> = _millionaireAudienceUsed.asStateFlow()

    private val _millionaireShowAudience = MutableStateFlow(false)
    val millionaireShowAudience: StateFlow<Boolean> = _millionaireShowAudience.asStateFlow()

    private val _millionaireFriendUsed = MutableStateFlow(false)
    val millionaireFriendUsed: StateFlow<Boolean> = _millionaireFriendUsed.asStateFlow()

    private val _millionaireFriendTip = MutableStateFlow<String?>(null)
    val millionaireFriendTip: StateFlow<String?> = _millionaireFriendTip.asStateFlow()

    private val _millionaireGameOver = MutableStateFlow(false)
    val millionaireGameOver: StateFlow<Boolean> = _millionaireGameOver.asStateFlow()

    private val _millionaireWonMillion = MutableStateFlow(false)
    val millionaireWonMillion: StateFlow<Boolean> = _millionaireWonMillion.asStateFlow()

    private val _millionairePrizeWon = MutableStateFlow(0)
    val millionairePrizeWon: StateFlow<Int> = _millionairePrizeWon.asStateFlow()

    fun startMillionaireGame() {
        audioEffects.playClick()
        val questions = ArabicGatheringGamesRepository.millionaireQuestions
        _millionaireList.value = questions
        _millionaireLevel.value = 1
        _millionaireQuestion.value = questions[0]
        _millionaireSelectedOption.value = null
        _millionaireIsCorrect.value = null
        _millionaireFiftyFiftyUsed.value = false
        _millionaireEliminatedIndices.value = emptyList()
        _millionaireAudienceUsed.value = false
        _millionaireShowAudience.value = false
        _millionaireFriendUsed.value = false
        _millionaireFriendTip.value = null
        _millionaireGameOver.value = false
        _millionaireWonMillion.value = false
        _millionairePrizeWon.value = 0
        navigateTo(GameScreen.MillionaireScreen)
    }

    fun selectMillionaireOption(index: Int) {
        if (_millionaireSelectedOption.value != null || _millionaireGameOver.value) return
        _millionaireSelectedOption.value = index
        val currentQ = _millionaireQuestion.value
        val isCorrect = index == currentQ.correctIndex
        _millionaireIsCorrect.value = isCorrect

        if (isCorrect) {
            audioEffects.playVictory()
            _millionairePrizeWon.value = currentQ.prizeValue
            repository.addPointsAndCoins(currentQ.prizeValue / 10, currentQ.prizeValue / 50)
            recordPartyGameScore("المليون العربي", "party", currentQ.prizeValue, "المستوى ${_millionaireLevel.value} • ${currentQ.prizeValue} نقطة 💰")
            if (_millionaireLevel.value >= 15) {
                _millionaireWonMillion.value = true
                _millionaireGameOver.value = true
            }
        } else {
            audioEffects.playWrong()
            _millionaireGameOver.value = true
            // Fall back to safety threshold
            val fallbackPrize = when {
                _millionaireLevel.value > 10 -> 32000
                _millionaireLevel.value > 5 -> 1000
                else -> 0
            }
            _millionairePrizeWon.value = fallbackPrize
        }
    }

    fun useMillionaireFiftyFifty() {
        if (_millionaireFiftyFiftyUsed.value || _millionaireSelectedOption.value != null) return
        audioEffects.playClick()
        _millionaireFiftyFiftyUsed.value = true
        val correct = _millionaireQuestion.value.correctIndex
        val wrongIndices = listOf(0, 1, 2, 3).filter { it != correct }.shuffled().take(2)
        _millionaireEliminatedIndices.value = wrongIndices
    }

    fun useMillionaireAudience() {
        if (_millionaireAudienceUsed.value || _millionaireSelectedOption.value != null) return
        audioEffects.playClick()
        _millionaireAudienceUsed.value = true
        _millionaireShowAudience.value = true
    }

    fun dismissMillionaireAudience() {
        _millionaireShowAudience.value = false
    }

    fun useMillionaireFriend() {
        if (_millionaireFriendUsed.value || _millionaireSelectedOption.value != null) return
        audioEffects.playClick()
        _millionaireFriendUsed.value = true
        _millionaireFriendTip.value = _millionaireQuestion.value.friendTip
    }

    fun dismissMillionaireFriend() {
        _millionaireFriendTip.value = null
    }

    fun withdrawMillionaire() {
        if (_millionaireGameOver.value) return
        audioEffects.playWhistle()
        _millionaireGameOver.value = true
    }

    fun nextMillionaireQuestion() {
        if (_millionaireLevel.value >= 15) return
        audioEffects.playClick()
        val nextLevel = _millionaireLevel.value + 1
        _millionaireLevel.value = nextLevel
        _millionaireQuestion.value = _millionaireList.value[nextLevel - 1]
        _millionaireSelectedOption.value = null
        _millionaireIsCorrect.value = null
        _millionaireEliminatedIndices.value = emptyList()
        _millionaireShowAudience.value = false
        _millionaireFriendTip.value = null
    }

    // =========================================================================
    // 9. الرابط العجيب / أربع كلمات وكلمة (FOUR CLUES ONE WORD)
    // =========================================================================
    private val _fourCluesList = MutableStateFlow(ArabicGatheringGamesRepository.fourCluesItems.shuffled())
    private val _fourCluesIndex = MutableStateFlow(0)
    private val _fourCluesCurrent = MutableStateFlow(ArabicGatheringGamesRepository.fourCluesItems[0])
    val fourCluesCurrent: StateFlow<FourCluesItem> = _fourCluesCurrent.asStateFlow()

    private val _fourCluesScore = MutableStateFlow(0)
    val fourCluesScore: StateFlow<Int> = _fourCluesScore.asStateFlow()

    private val _fourCluesSolved = MutableStateFlow(false)
    val fourCluesSolved: StateFlow<Boolean> = _fourCluesSolved.asStateFlow()

    private val _fourCluesFeedback = MutableStateFlow<String?>(null)
    val fourCluesFeedback: StateFlow<String?> = _fourCluesFeedback.asStateFlow()

    fun startFourCluesGame() {
        audioEffects.playClick()
        val list = ArabicGatheringGamesRepository.fourCluesItems.shuffled()
        _fourCluesList.value = list
        _fourCluesIndex.value = 0
        _fourCluesCurrent.value = list[0]
        _fourCluesScore.value = 0
        _fourCluesSolved.value = false
        _fourCluesFeedback.value = null
        navigateTo(GameScreen.FourCluesScreen)
    }

    fun submitFourCluesAnswer(guess: String) {
        val trimmed = guess.trim()
        if (trimmed.isEmpty()) return
        val current = _fourCluesCurrent.value

        val isCorrect = trimmed.equals(current.answer, ignoreCase = true) ||
                trimmed.equals(current.answer.removePrefix("ال"), ignoreCase = true)

        if (isCorrect) {
            _fourCluesSolved.value = true
            _fourCluesScore.value += 20
            audioEffects.playVictory()
            repository.addPointsAndCoins(20, 10)
            _fourCluesFeedback.value = "عبقري! الرابط المشترك هو (${current.answer}) 🌟"
            recordPartyGameScore("الرابط العجيب", "party", 20, "حل رابط ${current.categoryHint} (${current.answer}) 🔍")
        } else {
            audioEffects.playWrong()
            _fourCluesFeedback.value = "إجابة غير صحيحة، فكر في الرابط المشترك جيداً! 🤔"
        }
    }

    fun nextFourClues() {
        audioEffects.playClick()
        val list = _fourCluesList.value
        val nextIdx = (_fourCluesIndex.value + 1) % list.size
        _fourCluesIndex.value = nextIdx
        _fourCluesCurrent.value = list[nextIdx]
        _fourCluesSolved.value = false
        _fourCluesFeedback.value = null
    }

    fun startBedoonKalamGame(mode: BedoonKalamMode = BedoonKalamMode.EMOJI_GUESS) = startBedoonKalam(mode)

    // =========================================================================
    // 10. وضع التجمع على جهاز واحد (PASS & PLAY TEAMS)
    // =========================================================================
    private val _gatheringTeamState = MutableStateFlow(GatheringTeamState())
    val gatheringTeamState: StateFlow<GatheringTeamState> = _gatheringTeamState.asStateFlow()

    fun startPassAndPlay(teamRed: String = "الفريق الأحمر 🔴", teamBlue: String = "الفريق الأزرق 🔵", rounds: Int = 5) {
        audioEffects.playWhistle()
        _gatheringTeamState.value = GatheringTeamState(
            teamRedName = teamRed.ifBlank { "الفريق الأحمر 🔴" },
            teamBlueName = teamBlue.ifBlank { "الفريق الأزرق 🔵" },
            redScore = 0,
            blueScore = 0,
            currentTurnTeam = "RED",
            currentRound = 1,
            totalRounds = rounds,
            isGameFinished = false
        )
        navigateTo(GameScreen.PassAndPlayScreen)
    }

    fun awardPassAndPlayPoints(points: Int) {
        audioEffects.playVictory()
        val current = _gatheringTeamState.value
        val newRed = if (current.currentTurnTeam == "RED") current.redScore + points else current.redScore
        val newBlue = if (current.currentTurnTeam == "BLUE") current.blueScore + points else current.blueScore

        val isRedTurn = current.currentTurnTeam == "RED"
        val activeName = if (isRedTurn) current.teamRedName else current.teamBlueName
        recordPartyGameScore("ديربي التجمعات", "party", points, "$activeName • جولة ${current.currentRound} 👥")

        val nextTeam = if (isRedTurn) "BLUE" else "RED"
        val nextRound = if (!isRedTurn) current.currentRound + 1 else current.currentRound
        val isFinished = !isRedTurn && (nextRound > current.totalRounds)

        _gatheringTeamState.value = current.copy(
            redScore = newRed,
            blueScore = newBlue,
            currentTurnTeam = nextTeam,
            currentRound = nextRound,
            isGameFinished = isFinished
        )
    }

    fun switchPassAndPlayTurn() {
        audioEffects.playClick()
        val current = _gatheringTeamState.value
        val isRedTurn = current.currentTurnTeam == "RED"
        val nextTeam = if (isRedTurn) "BLUE" else "RED"
        val nextRound = if (!isRedTurn) current.currentRound + 1 else current.currentRound
        val isFinished = !isRedTurn && (nextRound > current.totalRounds)

        _gatheringTeamState.value = current.copy(
            currentTurnTeam = nextTeam,
            currentRound = nextRound,
            isGameFinished = isFinished
        )
    }

    fun resetPassAndPlay() {
        audioEffects.playClick()
        val current = _gatheringTeamState.value
        _gatheringTeamState.value = current.copy(
            redScore = 0,
            blueScore = 0,
            currentTurnTeam = "RED",
            currentRound = 1,
            isGameFinished = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        audioEffects.release()
        speechToTextManager.destroy()
        pvpTimerJob?.cancel()
        pvpOpponentJob?.cancel()
        adJob?.cancel()
        tsTimerJob?.cancel()
        autobusTimerJob?.cancel()
        autobusAiJob?.cancel()
        fiveSecondsTimerJob?.cancel()
        bedoonKalamTimerJob?.cancel()
        tabooTimerJob?.cancel()
        roomSessionJob?.cancel()
        realtimeRefreshJob?.cancel()

        // إغلاق اتصال Realtime صراحةً: سيب WebSocket مفتوح بعد موت الـViewModel
        // معناه خيط ونبضات شغالة لأبد الآبد واستنزاف بطارية بلا أي مستمع
        realtimeClient.dispose()

        // إيقاف سيرفر الهوتسبوت وقفل الـMulticast: onCleared بيُستدعى عند
        // الخروج من التطبيق مش عند لفّ الشاشة، فالجلسة بتكمل طول ما التطبيق مفتوح
        LanGameManager.shutdown()
    }
}

// -----------------------------------------------------------------------------
// PRIVATE HELPER FUNCTIONS
// -----------------------------------------------------------------------------
/** عدد الصفوف المسحوبة من لوحة الصدارة السحابية. */
private const val CLOUD_LEADERBOARD_LIMIT = 25

/**
 * فترة تحديث جلسة المباراة المباشرة (مللي ثانية) عندما لا يوجد دفع لحظي.
 * 3 ثواني توازن بين الإحساس باللحظية واستهلاك الشبكة/البطارية.
 */
private const val ROOM_SESSION_POLL_MS = 3_000L

/**
 * فترة التحديث عندما يكون الاشتراك اللحظي (Realtime) شغالاً: الحلبة هنا
 * شبكة أمان فقط، فنبطّئها لتوفير البطارية والبيانات.
 */
private const val ROOM_SESSION_IDLE_POLL_MS = 15_000L

/**
 * تجميع انفجارات أحداث Realtime (مللي ثانية).
 * تحديث مشارك واحد بيسبب عدة أحداث متتالية — بندمجهم في مزامنة واحدة.
 */
private const val REALTIME_REFRESH_DEBOUNCE_MS = 400L

/**
 * أقل زمن بين مزامنتين لأسئلة البوت (مللي ثانية).
 *
 * عشر دقايق كفاية إن لاعب يفتح أكتر من لعبة ورا بعض من غير ما نرمي طلبات
 * شبكة زيادة، وفي نفس الوقت لو البوت نشر حالاً اللاعب هيشوف الجديد في
 * الجلسة الجاية.
 */
private const val QUESTION_FEED_MIN_REFRESH_MS = 10 * 60 * 1000L

/** نتائج تجريبية للوحة الصدارة عندما لا يوجد سيرفر مُهيّأ. */
private fun demoCloudScores(): List<SupabaseScore> = listOf(
    SupabaseScore(id = "demo_1", playerName = "عمر المدريدي 👑", points = 5420, coins = 1100, levelTitle = "أسطورة الملاعب", favoriteClub = "ريال مدريد"),
    SupabaseScore(id = "demo_2", playerName = "ياسين الأطلسي 🇲🇦", points = 4950, coins = 980, levelTitle = "محترف دولي", favoriteClub = "الوداد البيضاوي"),
    SupabaseScore(id = "demo_3", playerName = "أحمد الفراعنة 🇪🇬", points = 4310, coins = 820, levelTitle = "كابتن الملعب", favoriteClub = "الأهلي المصري")
)

/**
 * غرف تجريبية تُعرض عندما لا توجد غرف حقيقية (سيرفر غير مُهيّأ أو قائمة فارغة)،
 * حتى تبقى شاشة "غرف اللعب" حيّة وقابلة للتجربة.
 */
private fun demoOnlineRooms(): List<GameRoom> = listOf(
    GameRoom(
        roomCode = "STAD99",
        hostName = "كابتن ساري ⚽",
        gameType = "thirty_seconds",
        status = com.example.data.room.RoomStatus.WAITING,
        participantCount = 3,
        maxPlayers = 6,
        region = "MENA",
        createdAtMillis = System.currentTimeMillis() - 60_000
    ),
    GameRoom(
        roomCode = "BUZZ22",
        hostName = "فارس الكالتشيو 🇮🇹",
        gameType = "buzzer",
        status = com.example.data.room.RoomStatus.WAITING,
        participantCount = 2,
        maxPlayers = 4,
        region = "GULF",
        createdAtMillis = System.currentTimeMillis() - 180_000
    ),
    GameRoom(
        roomCode = "GRID33",
        hostName = "البروفيسور 🧠",
        gameType = "tictactoe",
        status = com.example.data.room.RoomStatus.PLAYING,
        participantCount = 2,
        maxPlayers = 2,
        region = "NAfrica",
        createdAtMillis = System.currentTimeMillis() - 420_000
    )
)

private fun generateChallengeCode(): String {
    val prefixes = listOf("CHAMP", "KICK", "TAHADDI", "MADRID", "BARCA", "CAPTAIN", "GOAL", "LEGEND")
    val num = (1000..9999).random()
    return "${prefixes.random()}-$num"
}

private fun defaultPresetFriends(): List<FriendlyChallengeInfo> = listOf(
    FriendlyChallengeInfo(
        code = "BARCA-10",
        friendName = "كابتن زياد",
        friendAvatar = "🔵🔴",
        friendClub = "برشلونة",
        difficulty = GameDifficulty.MEDIUM,
        message = "جاهز لكلاسيكو ناري! يلا وريني معلوماتك ⚽"
    ),
    FriendlyChallengeInfo(
        code = "REAL-07",
        friendName = "عمر المدريدي",
        friendAvatar = "👑",
        friendClub = "ريال مدريد",
        difficulty = GameDifficulty.HARD,
        message = "تحدي الأساطير الودي، مين ملك المونديال الحقيقي؟ 🏆"
    ),
    FriendlyChallengeInfo(
        code = "HILAL-24",
        friendName = "سعود الزعيم",
        friendAvatar = "💙",
        friendClub = "الهلال السعودي",
        difficulty = GameDifficulty.MEDIUM,
        message = "تحدي كروي عربي أصيل! مستعد للبداية؟ 🌟"
    ),
    FriendlyChallengeInfo(
        code = "EGYPT-11",
        friendName = "أحمد فخر العرب",
        friendAvatar = "🇪🇬",
        friendClub = "ليفربول",
        difficulty = GameDifficulty.EASY,
        message = "مواجهة سريعة وخفيفة بين الأصدقاء! ⚽"
    )
)

private fun defaultFriendlyHistory(): List<FriendlyMatchHistoryItem> = listOf(
    FriendlyMatchHistoryItem(
        id = "fm_1",
        friendName = "كابتن زياد",
        friendAvatar = "🔵🔴",
        userScore = 4,
        friendScore = 2,
        isWin = true,
        challengeCode = "BARCA-10",
        timeAgo = "اليوم 16:30"
    ),
    FriendlyMatchHistoryItem(
        id = "fm_2",
        friendName = "عمر المدريدي",
        friendAvatar = "👑",
        userScore = 3,
        friendScore = 3,
        isWin = false,
        challengeCode = "REAL-07",
        timeAgo = "أمس 21:10"
    )
)

