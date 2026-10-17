package com.example.data

// ---------------------------------------------------------------------------
// 1. AUTOBUS COMPLETE (أتوبيس كومبليت / حافلة الكلمات)
// ---------------------------------------------------------------------------

enum class AutobusFieldType(val titleArabic: String, val icon: String, val placeholder: String) {
    BOY("ولد", "👦", "اسم ولد..."),
    GIRL("بنت", "👧", "اسم بنت..."),
    ANIMAL("حيوان", "🦁", "اسم حيوان..."),
    PLANT("نبات", "🌿", "نبات أو فاكهة أو خضار..."),
    OBJECT("جماد", "📦", "جماد أو أداة..."),
    COUNTRY("بلاد/مدينة", "🌍", "دولة أو عاصمة أو مدينة..."),
    JOB("مهنة", "💼", "وظيفة أو مهنة..."),
    FOOD("أكلة", "🍲", "طعام أو وجبة...")
}

data class AutobusLetterData(
    val letter: Char,
    val letterDisplay: String,
    val sampleBoy: String,
    val sampleGirl: String,
    val sampleAnimal: String,
    val samplePlant: String,
    val sampleObject: String,
    val sampleCountry: String,
    val sampleJob: String,
    val sampleFood: String
)

data class AutobusUserAnswers(
    var boy: String = "",
    var girl: String = "",
    var animal: String = "",
    var plant: String = "",
    var obj: String = "",
    var country: String = "",
    var job: String = "",
    var food: String = ""
) {
    fun getAnswer(type: AutobusFieldType): String = when (type) {
        AutobusFieldType.BOY -> boy
        AutobusFieldType.GIRL -> girl
        AutobusFieldType.ANIMAL -> animal
        AutobusFieldType.PLANT -> plant
        AutobusFieldType.OBJECT -> obj
        AutobusFieldType.COUNTRY -> country
        AutobusFieldType.JOB -> job
        AutobusFieldType.FOOD -> food
    }

    fun setAnswer(type: AutobusFieldType, value: String) {
        when (type) {
            AutobusFieldType.BOY -> boy = value
            AutobusFieldType.GIRL -> girl = value
            AutobusFieldType.ANIMAL -> animal = value
            AutobusFieldType.PLANT -> plant = value
            AutobusFieldType.OBJECT -> obj = value
            AutobusFieldType.COUNTRY -> country = value
            AutobusFieldType.JOB -> job = value
            AutobusFieldType.FOOD -> food = value
        }
    }

    fun filledCount(): Int {
        var count = 0
        if (boy.isNotBlank()) count++
        if (girl.isNotBlank()) count++
        if (animal.isNotBlank()) count++
        if (plant.isNotBlank()) count++
        if (obj.isNotBlank()) count++
        if (country.isNotBlank()) count++
        if (job.isNotBlank()) count++
        if (food.isNotBlank()) count++
        return count
    }
}

data class AutobusFieldResult(
    val type: AutobusFieldType,
    val playerAnswer: String,
    val aiAnswer: String,
    val isValid: Boolean,
    val scoreAwarded: Int,
    val feedback: String
)

data class AutobusRoundResult(
    val letter: Char,
    val totalScore: Int,
    val aiTotalScore: Int,
    val fieldResults: List<AutobusFieldResult>,
    val timeTakenSeconds: Int
)

// ---------------------------------------------------------------------------
// 2. FIVE SECONDS CHALLENGE (تحدي الـ 5 ثواني)
// ---------------------------------------------------------------------------

data class FiveSecondsPrompt(
    val id: String,
    val prompt: String,
    val category: String,
    val icon: String,
    val exampleAnswers: List<String>
)

data class FiveSecondsRound(
    val prompt: FiveSecondsPrompt,
    val countAchieved: Int,
    val success: Boolean,
    val secondsLeft: Float
)

// ---------------------------------------------------------------------------
// 3. WOULD YOU RATHER (لو خيروك؟)
// ---------------------------------------------------------------------------

data class WouldYouRatherQuestion(
    val id: String,
    val category: String,
    val optionA: String,
    val optionB: String,
    val percentageA: Int,
    val totalVotes: Int = 14500,
    val funFact: String = ""
)

// ---------------------------------------------------------------------------
// 4. GENERAL RIDDLES & PUZZLES (فوازير وذكاء)
// ---------------------------------------------------------------------------

data class GeneralRiddle(
    val id: String,
    val title: String,
    val question: String,
    val hints: List<String>,
    val answer: String,
    val acceptableAnswers: List<String>,
    val funExplanation: String
)

// ---------------------------------------------------------------------------
// REPOSITORY FOR PARTY & GENERAL GAMES
// ---------------------------------------------------------------------------

object PartyGamesRepository {

    val autobusLetters = listOf(
        AutobusLetterData('أ', "أ (الألف)", "أحمد", "أميرة", "أسد", "أرز", "إبرة", "ألمانيا", "أستاذ", "أرز باللبن"),
        AutobusLetterData('ب', "ب (الباء)", "باسم", "بسمة", "بطريق", "برتقال", "برميل", "برازيل", "بناء", "بيتزا"),
        AutobusLetterData('ت', "ت (التاء)", "تامر", "تسنيم", "تمساح", "تفاح", "تلفاز", "تركيا", "تاجر", "تونة"),
        AutobusLetterData('ث', "ث (الثاء)", "ثابت", "ثريا", "ثعلب", "ثوم", "ثلاجة", "ثيساليا", "ثري", "ثريد"),
        AutobusLetterData('ج', "ج (الجيم)", "جمال", "جميلة", "جمل", "جزر", "جرس", "جزائر", "جزار", "جبنة"),
        AutobusLetterData('ح', "ح (الحاء)", "حسن", "حبيبة", "حصان", "حمص", "حبل", "حلب", "حداد", "حواوشي"),
        AutobusLetterData('خ', "خ (الخاء)", "خالد", "خلود", "خروف", "خس", "خاتم", "خرطوم", "خياط", "خبز"),
        AutobusLetterData('د', "د (الدال)", "داوود", "داليا", "دب", "دوار الشمس", "دفتر", "دبي", "دكتور", "دجاج مشوي"),
        AutobusLetterData('ر', "ر (الراء)", "رامي", "ريم", "راكون", "رمان", "راديو", "روسيا", "رسام", "رقاق"),
        AutobusLetterData('ز', "ز (الزاي)", "زياد", "زينب", "زرافة", "زعتر", "زر", "زيمبابوي", "زجّاج", "زبادي"),
        AutobusLetterData('س', "س (السين)", "سامي", "سارة", "سمكة", "سبانخ", "ساعة", "سودان", "سباك", "سوشي"),
        AutobusLetterData('ش', "ش (الشين)", "شريف", "شهد", "شمبانزي", "شمام", "شباك", "شيلي", "شرطي", "شوربة عدس"),
        AutobusLetterData('ص', "ص (الصاد)", "صلاح", "صباح", "صقر", "صبار", "صندوق", "صومال", "صياد", "صيادية"),
        AutobusLetterData('ط', "ط (الطاء)", "طارق", "طيبة", "طاووس", "طماطم", "طاولة", "طوكيو", "طيار", "طعمية"),
        AutobusLetterData('ع', "ع (العين)", "علي", "عائشة", "عصفور", "عنب", "علم", "عُمان", "عالم", "عدس"),
        AutobusLetterData('ف', "ف (الفاء)", "فارس", "فاطمة", "فهد", "فراولة", "فانوس", "فرنسا", "فنان", "فتة"),
        AutobusLetterData('ق', "ق (القاف)", "قاسم", "قمر", "قرد", "قرنبيط", "قلم", "قطر", "قاضي", "قراقيش"),
        AutobusLetterData('ك', "ك (الكاف)", "كريم", "كارما", "كنغر", "كرنب", "كرسي", "كويت", "كاتب", "كشري"),
        AutobusLetterData('ل', "ل (اللام)", "ليث", "ليلى", "لقلق", "ليمون", "لوحة", "لبنان", "لاعب", "لازانيا"),
        AutobusLetterData('م', "م (الميم)", "محمد", "مريم", "ماعز", "مانجو", "مفتاح", "مصر", "مهندس", "ملوخية"),
        AutobusLetterData('ن', "ن (النون)", "نادر", "نور", "نمر", "نعناع", "نظارة", "نيجيريا", "نجار", "ناجتس"),
        AutobusLetterData('ه', "هـ (الهاء)", "هاني", "هدى", "هدهد", "هيل", "هاتف", "هند", "هدّاف", "همبرجر"),
        AutobusLetterData('و', "و (الواو)", "وليد", "وعد", "وشق", "ورد", "ورقة", "واشنطن", "وزير", "ورق عنب"),
        AutobusLetterData('ي', "ي (الياء)", "يوسف", "ياسمين", "يعسوب", "يوسفي", "ياقة", "يونان", "يوتيوبر", "يخنة")
    )

    fun getRandomLetter(): AutobusLetterData {
        return autobusLetters.random()
    }

    fun getLetterData(char: Char): AutobusLetterData {
        return autobusLetters.firstOrNull { it.letter == char } ?: autobusLetters[0]
    }

    fun evaluateAutobusAnswer(
        letter: Char,
        category: AutobusFieldType,
        userAnswerRaw: String,
        aiAnswer: String
    ): AutobusFieldResult {
        val trimmed = userAnswerRaw.trim()
        if (trimmed.isEmpty()) {
            return AutobusFieldResult(
                type = category,
                playerAnswer = "",
                aiAnswer = aiAnswer,
                isValid = false,
                scoreAwarded = 0,
                feedback = "فارغ (0 نقطة)"
            )
        }

        var normalized = trimmed
        if (letter != 'أ' && letter != 'ا' && letter != 'إ' && letter != 'آ') {
            if (normalized.startsWith("ال") && normalized.length > 2) {
                normalized = normalized.substring(2)
            }
        }

        val firstChar = normalized.firstOrNull() ?: ' '
        val matchLetter = when (letter) {
            'أ', 'إ', 'آ', 'ا' -> firstChar in listOf('أ', 'إ', 'آ', 'ا')
            'ه', 'ة' -> firstChar in listOf('ه', 'ة')
            'ي', 'ى' -> firstChar in listOf('ي', 'ى')
            else -> firstChar == letter
        }

        if (!matchLetter) {
            return AutobusFieldResult(
                type = category,
                playerAnswer = trimmed,
                aiAnswer = aiAnswer,
                isValid = false,
                scoreAwarded = 0,
                feedback = "لا يبدأ بحرف $letter"
            )
        }

        if (normalized.length < 2) {
            return AutobusFieldResult(
                type = category,
                playerAnswer = trimmed,
                aiAnswer = aiAnswer,
                isValid = false,
                scoreAwarded = 0,
                feedback = "كلمة قصيرة جداً"
            )
        }

        val isDuplicateWithAi = trimmed.equals(aiAnswer, ignoreCase = true) ||
                normalized.equals(aiAnswer.removePrefix("ال"), ignoreCase = true)

        val score = if (isDuplicateWithAi) 5 else 10
        val feedback = if (isDuplicateWithAi) "تطابق مع المنافس (5 نقاط)" else "إجابة فريدة صحيحة (+10)"

        return AutobusFieldResult(
            type = category,
            playerAnswer = trimmed,
            aiAnswer = aiAnswer,
            isValid = true,
            scoreAwarded = score,
            feedback = feedback
        )
    }

    // ---------------------------------------------------------------------------
    // FIVE SECONDS PROMPTS
    // ---------------------------------------------------------------------------
    val fiveSecondsPrompts = listOf(
        FiveSecondsPrompt("fs1", "اذكر 3 فواكه لونها أحمر في 5 ثواني!", "طعام", "🍎", listOf("فراولة", "تفاح", "كرز", "بطيخ", "رمان")),
        FiveSecondsPrompt("fs2", "اذكر 3 دول عربية تبدأ بحرف الميم!", "جغرافيا", "🌍", listOf("مصر", "المغرب", "موريتانيا")),
        FiveSecondsPrompt("fs3", "اذكر 3 أشياء تجدها في المطبخ!", "حياة يومية", "🍳", listOf("ملعقة", "سكين", "ثلاجة", "طنجرة", "صحن")),
        FiveSecondsPrompt("fs4", "اذكر 3 حيوانات تعيش في الماء فقط!", "طبيعة", "🐬", listOf("دلفين", "قرش", "حوت", "أخطبوط", "سمك")),
        FiveSecondsPrompt("fs5", "اذكر 3 أكلات شعبية مشهورة!", "أطعمة", "🍲", listOf("كشري", "ملوخية", "شاورما", "منسف", "فلافل")),
        FiveSecondsPrompt("fs6", "اذكر 3 لغات غير العربية والإنجليزية!", "ثقافة عامة", "🗣️", listOf("فرنسية", "إسبانية", "ألمانية", "روسية", "يابانية")),
        FiveSecondsPrompt("fs7", "اذكر 3 وسائل مواصلات برية!", "حياة يومية", "🚌", listOf("حافلة", "قطار", "سيارة", "دراجة", "مترو")),
        FiveSecondsPrompt("fs8", "اذكر 3 أشياء تشتريها من الصيدلية!", "حياة يومية", "💊", listOf("مسكن", "لاصق جروح", "فيتامين", "قطرة", "شاش")),
        FiveSecondsPrompt("fs9", "اذكر 3 عواصم أوروبية شهيرة!", "جغرافيا", "🏛️", listOf("باريس", "لندن", "مدريد", "روما", "برلين")),
        FiveSecondsPrompt("fs10", "اذكر 3 أشياء تضعها في حقيبة السفر!", "حياة يومية", "🧳", listOf("ملابس", "جواز سفر", "شاحن", "عطر", "فرشاة أسنان")),
        FiveSecondsPrompt("fs11", "اذكر 3 مهن تتطلب ارتداء زي موحد!", "مهن", "👮", listOf("طبيب", "شرطي", "طيار", "جندي", "إطفائي")),
        FiveSecondsPrompt("fs12", "اذكر 3 كواكب في المجموعة الشمسية!", "علوم", "🪐", listOf("المريخ", "المشتري", "زحل", "عطارد", "الزهرة")),
        FiveSecondsPrompt("fs13", "اذكر 3 حلويات شرقية شهيرة!", "حلويات", "🍯", listOf("كنافة", "بسبوسة", "بقلاوة", "قطايف", "أم علي")),
        FiveSecondsPrompt("fs14", "اذكر 3 أجهزة إلكترونية في غرفتك!", "تكنولوجيا", "📱", listOf("هاتف", "تلفاز", "حاسوب", "مكيف", "سماعات")),
        FiveSecondsPrompt("fs15", "اذكر 3 مدن سياحية ساحلية عربية!", "سياحة", "🏖️", listOf("الإسكندرية", "شرم الشيخ", "دبي", "طنجة", "بيروت")),
        FiveSecondsPrompt("fs16", "اذكر 3 أشياء تجدها في حقيبة المدرسة!", "دراسة", "🎒", listOf("دفتر", "قلم", "مسطرة", "ممحاة", "كتاب")),
        FiveSecondsPrompt("fs17", "اذكر 3 فصول من فصول السنة!", "طبيعة", "🍂", listOf("الصيف", "الشتاء", "الربيع", "الخريف")),
        FiveSecondsPrompt("fs18", "اذكر 3 طيور لا تستطيع الطيران!", "طبيعة", "🐧", listOf("نعامة", "بطريق", "دجاجة", "كيوي")),
        FiveSecondsPrompt("fs19", "اذكر 3 ألوان تظهر في إشارات المرور!", "حياة يومية", "🚦", listOf("أحمر", "أصفر", "أخضر")),
        FiveSecondsPrompt("fs20", "اذكر 3 أعضاء في جسم الإنسان!", "علوم", "🫀", listOf("قلب", "كبد", "رئة", "معدة", "دماغ"))
    )

    // ---------------------------------------------------------------------------
    // WOULD YOU RATHER QUESTIONS (لو خيروك)
    // ---------------------------------------------------------------------------
    val wouldYouRatherQuestions = listOf(
        WouldYouRatherQuestion(
            id = "wyr1",
            category = "قدرات خارقة",
            optionA = "القدرة على قراءة أفكار الآخرين 🧠",
            optionB = "القدرة على الاختفاء وقتما تشاء 🥷",
            percentageA = 48,
            funFact = "أغلب الناس يخافون من معرفة ما يفكر فيه الآخرون حقاً!"
        ),
        WouldYouRatherQuestion(
            id = "wyr2",
            category = "حياة يومية",
            optionA = "العيش بدون إنترنت لمدة سنة كاملة 📵",
            optionB = "العيش بدون أكلتك المفضلة طوال حياتك 🚫🍔",
            percentageA = 22,
            funFact = "78% فضلوا التنازل عن أكلتهم المفضلة على الاستغناء عن الإنترنت!"
        ),
        WouldYouRatherQuestion(
            id = "wyr3",
            category = "سفر وغرائب",
            optionA = "السفر إلى المستقبل بعد 100 سنة 🚀",
            optionB = "العودة إلى الماضي لتعيش في العصر الأندلسي 🏰",
            percentageA = 65,
            funFact = "الفضول لرؤية التكنولوجيا القادمة جذب أغلبية اللاعبين!"
        ),
        WouldYouRatherQuestion(
            id = "wyr4",
            category = "تحديات مضحكة",
            optionA = "أن تتكلم بصوت غنائي دائماً مثل الأوبرا 🎶",
            optionB = "أن تضحك بصوت عالٍ بعد كل جملة تقولها 🤣",
            percentageA = 57,
            funFact = "الغناء أسهل في المواقف المحرجة من الضحك الهستيري!"
        ),
        WouldYouRatherQuestion(
            id = "wyr5",
            category = "مال وحظ",
            optionA = "الحصول على مليون دولار فوراً 💰",
            optionB = "فرصة 50% للحصول على 50 مليون دولار 🎲",
            percentageA = 74,
            funFact = "عصفور في اليد خير من عشرة على الشجرة!"
        ),
        WouldYouRatherQuestion(
            id = "wyr6",
            category = "حياة واجتماعيات",
            optionA = "أن تكون الأذكى في العالم لكن لا أحد يصدقك 💡",
            optionB = "أن تكون عادياً والجميع يستمع إليك ويحترمك 👑",
            percentageA = 31,
            funFact = "العزلة الفكرية مخيفة لأغلب الناس!"
        ),
        WouldYouRatherQuestion(
            id = "wyr7",
            category = "أطعمة",
            optionA = "أكل بيتزا كل يوم لبقية حياتك 🍕",
            optionB = "أكل برجر كل يوم لبقية حياتك 🍔",
            percentageA = 54,
            funFact = "البيتزا فازت بفارق بسيط لتنوع مكوناتها!"
        ),
        WouldYouRatherQuestion(
            id = "wyr8",
            category = "مغامرة",
            optionA = "قضاء ليلة وحيداً في جزيرة مهجورة 🏝️",
            optionB = "قضاء ليلة وحيداً في منزل قديم مسكون 👻",
            percentageA = 83,
            funFact = "الطبيعة والجزيرة أفضل ألف مرة من الأشباح!"
        ),
        WouldYouRatherQuestion(
            id = "wyr9",
            category = "عادات",
            optionA = "الاستيقاظ يومياً الساعة 5 صباحاً بنشاط كامل 🌅",
            optionB = "السهر حتى 3 فجراً بدون أي شعور بالتعب 🌃",
            percentageA = 42,
            funFact = "محبو الليل يسيطرون دائماً!"
        ),
        WouldYouRatherQuestion(
            id = "wyr10",
            category = "ذكاء وتقنية",
            optionA = "معرفة تاريخ وفاتك بدقة ⏳",
            optionB = "معرفة سبب وفاتك دون التاريخ 🔍",
            percentageA = 19,
            funFact = "معرفة التاريخ تسبب قلقاً مستمراً لدى 81% من المشاركين!"
        )
    )

    // ---------------------------------------------------------------------------
    // GENERAL RIDDLES (فوازير وألغاز الذكاء)
    // ---------------------------------------------------------------------------
    val generalRiddles = listOf(
        GeneralRiddle(
            id = "gr1",
            title = "لغز الشيء العجيب",
            question = "ما هو الشيء الذي إذا أخذت منه كَبُر، وإذا وضعت فيه صَغُر؟",
            hints = listOf("تراه في الأرض أو الجدران", "يبدأ بحرف الحاء", "كلما حفرت أكثر يتسع"),
            answer = "الحفرة",
            acceptableAnswers = listOf("حفرة", "الحفرة", "حفره", "الحفره"),
            funExplanation = "الحفرة! كلما أخذت منها تراباً كبر حجمها، وإذا ردمتها صغرت!"
        ),
        GeneralRiddle(
            id = "gr2",
            title = "لغز المسافر بلا أرجل",
            question = "يمشي بلا أرجل، ويبكي بلا عينين، ويطير بلا أجنحة، فما هو؟",
            hints = listOf("يأتي من السماء", "مرتبط بالطقس والمطر", "يتجمع في كتل بيضاء أو رمادية"),
            answer = "السحاب",
            acceptableAnswers = listOf("السحاب", "سحاب", "الغيم", "الغيوم", "السحابة", "المطر"),
            funExplanation = "السحاب أو الغيوم! تمشي مع الرياح وتهطل أمطارها كالبكاء!"
        ),
        GeneralRiddle(
            id = "gr3",
            title = "لغز الزمن",
            question = "كلما زاد نقص، ولا يمكن استرجاع ما مضى منه أبداً؟",
            hints = listOf("يقاس بالسنوات والأيام", "يبدأ بحرف العين", "يملكه كل إنسان منذ ولادته"),
            answer = "العمر",
            acceptableAnswers = listOf("العمر", "عمر", "عمر الإنسان"),
            funExplanation = "العمر! كلما زادت سنين حياتك نقص ما تبقى من عمرك!"
        ),
        GeneralRiddle(
            id = "gr4",
            title = "لغز الصديق الصامت",
            question = "له أسنان كثيرة ولكنه لا يعض أحداً، فما هو؟",
            hints = listOf("تستخدمه يومياً أمام المرآة", "مصنوع من البلاستيك أو الخشب", "يُرتب الشعر"),
            answer = "المشط",
            acceptableAnswers = listOf("المشط", "مشط", "المشط للشعر"),
            funExplanation = "المشط! أسنانه ترتب الشعر ولا تؤذي أحداً!"
        ),
        GeneralRiddle(
            id = "gr5",
            title = "لغز الشيء الصغير المفيد",
            question = "يولد كبيراً ويموت صغيراً، ويساعدك في التعبير أو الإنارة؟",
            hints = listOf("قد يكون للكتابة أو للضوء", "يذوب أو يبرى تدريجياً", "الشمعة أو قلم الرصاص"),
            answer = "الشمعة",
            acceptableAnswers = listOf("الشمعة", "شمعة", "القلم", "قلم رصاص", "شمع"),
            funExplanation = "الشمعة تذوب مع الوقت، وكذلك قلم الرصاص يقصر كلما بريته!"
        ),
        GeneralRiddle(
            id = "gr6",
            title = "لغز الخضار الباكي",
            question = "شيء تذبحه بنفسك وتبكي عليه دمعاً حقيقياً؟",
            hints = listOf("موجود في كل مطبخ", "له قشرة رقيقة ورائحة نفاذة", "يبدأ بحرف الباء"),
            answer = "البصل",
            acceptableAnswers = listOf("البصل", "بصل", "بصلة", "البصلة"),
            funExplanation = "البصل! عندما تقطعه تدمع عيناك من غازاته الطبيعية!"
        ),
        GeneralRiddle(
            id = "gr7",
            title = "لغز الرفيق اليومي",
            question = "يحملك طوال النهار وتضعه تحت السرير في الليل، وتحمله إذا مشيت حافياً؟",
            hints = listOf("ترتديه في قدميك", "مصنوع من الجلد أو القماش", "يبدأ بحرف الحاء"),
            answer = "الحذاء",
            acceptableAnswers = listOf("الحذاء", "حذاء", "الجزمة", "الشوز"),
            funExplanation = "الحذاء! يحملك عند المشي وتحمله بيدك إذا نزعته!"
        ),
        GeneralRiddle(
            id = "gr8",
            title = "لغز الباب المفقود",
            question = "بيت ليس له أبواب ولا نوافذ، ويسكنه المعنى الجميل؟",
            hints = listOf("تسمعه في القصائد والأشعار", "يتكون من صدر وعجز", "بيت..."),
            answer = "بيت الشعر",
            acceptableAnswers = listOf("بيت الشعر", "بيت شعر", "الشعر"),
            funExplanation = "بيت الشعر في القصيدة العربية الأصيلة!"
        ),
        GeneralRiddle(
            id = "gr9",
            title = "لغز الصوت العجيب",
            question = "يتكلم جميع لغات العالم بدون لسان، ويردد ما تقوله بالضبط؟",
            hints = listOf("تسمعه في الجبال والغرف الفارغة", "يرتد بعد كلامك بثوانٍ", "يبدأ بحرف الصاد"),
            answer = "الصدى",
            acceptableAnswers = listOf("الصدى", "صدى", "صدى الصوت"),
            funExplanation = "صدى الصوت! يعيد كل كلمة بكل لغة وبنفس نبرتك!"
        ),
        GeneralRiddle(
            id = "gr10",
            title = "لغز المخترق الصامت",
            question = "يدخل الغرفة دون أن يفتح الباب أو يكسر الزجاج، وينير كل ركن فيها؟",
            hints = listOf("يأتي من الشمس أو المصباح", "سرعته 300 ألف كم في الثانية", "يبدأ بحرف النون أو الضاد"),
            answer = "الضوء",
            acceptableAnswers = listOf("الضوء", "ضوء", "النور", "نور", "أشعة الشمس"),
            funExplanation = "الضوء أو النور! يخترق الزجاج الشفاف وينير كل مكان!"
        )
    )
}
