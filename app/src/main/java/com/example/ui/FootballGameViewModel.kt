package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.StadiumAudioEffects
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}

class FootballGameViewModel(application: Application) : AndroidViewModel(application) {
    val repository = FootballRepository(application)
    val audioEffects = StadiumAudioEffects(application)

    val userProfile: StateFlow<UserProfile> = repository.userProfile

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
        val questions = repository.getThirtySecondsQuestions().shuffled()
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
        repository.addPointsAndCoins(earnedPoints = earnedScore * 15, earnedCoins = earnedScore * 5)
        repository.updateThirtySecondsHighScore(earnedScore)
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
        val questions = repository.getSabahoQuestions().shuffled()
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
            repository.addPointsAndCoins(
                earnedPoints = _sabahoScore.value * 2,
                earnedCoins = (_sabahoScore.value * 0.8).toInt()
            )
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
        val list = repository.getTrueFalseQuestions().shuffled()
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
            is GameScreen.ThirtySecondsPlayers -> startPlayerMentionMode()
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
        val user = _userProfile.value
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
            pvpQuestions = repository.getPvPQuestions().shuffled().take(5)
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

        repository.addPointsAndCoins(
            earnedPoints = earnedPoints,
            earnedCoins = earnedCoins,
            gameTitle = "المواجهة المباشرة PvP"
        )

        if (won) {
            audioEffects.playWhistle()
        } else {
            audioEffects.playWrong()
        }

        val finalCommentary = when {
            won -> "🏆 فوز تاريخي ساحق لـ ${current.user.name} في كلاسيكو المواجهة المباشرة!"
            draw -> "🤝 مباراة ملحمية تنتهي بتعادل الفريقين واقتسام النقاط!"
            else -> "💔 خسر كابتن الملعب بفارق ضئيل، حظاً أوفر في المواجهة القادمة!"
        }

        _pvpState.value = current.copy(
            isMatchFinished = true,
            commentaryMessage = finalCommentary
        )
    }

    fun rematchPvP() {
        startPvPMatchSearch()
    }

    override fun onCleared() {
        super.onCleared()
        audioEffects.release()
        pvpTimerJob?.cancel()
        pvpOpponentJob?.cancel()
        adJob?.cancel()
        tsTimerJob?.cancel()
    }
}

