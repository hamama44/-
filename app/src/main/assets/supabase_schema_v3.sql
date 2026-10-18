-- ===========================================================================
-- ملعب التحدي — مخطط V3: أعمدة «البوت المُحدِّث» لجدول الأسئلة
-- ===========================================================================
--
-- الهدف: جدول public/questions يبقى آمناً للكتابة من بوت يعمل خارج التطبيق
-- (GitHub Actions) وللكتابة منه يومياً بدون ما يحصل تكرار أو يدخل محتوى
-- ناقص، وفي نفس الوقت التطبيق يقرأ منه بدون ما يشوف أسئلة تحت المراجعة.
--
-- ليه مش بنعمل جدول جديد؟ لأن public/questions موجود من V1 وفيه seed وأول
-- سياسات RLS، وإضافة عمود أقصر طريق وأقل كسراً للسلوك القائم (نفس سياسة V2:
-- توسيع الجدول الحالي وكشف view للقراءة).
--
-- التنفيذ: Supabase Dashboard → SQL Editor → الصق الملف كله → Run.
-- الملف idempotent: يتنفّذ أكثر من مرة بأمان.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1) أعمدة البوت
-- ---------------------------------------------------------------------------

-- بصمة المحتوى: sha256 على (game_type + نص السؤال موحّد + إجابة صحيحة موحّدة).
-- البوت بيحسبها، واليونيكون إن덱س تحت بيمنع التكرار على مستوى القاعدة نفسها
-- (مش على مستوى البوت لوحده) ⇒ أي بوت تاني أو تشغيل مزدوج مابيدبّش duplicate.
ALTER TABLE public.questions
    ADD COLUMN IF NOT EXISTS content_hash TEXT;

-- مصدر السؤال: seed = مُضمّن في التطبيق، bot = مولّد، curated = مُراجَع يدوياً،
-- admin = مُدخل من لوحة تحكم. مهم للتحليل وللمسح الانتقائي لاحقاً.
ALTER TABLE public.questions
    ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT 'seed';

-- حالة النشر. التطبيق ما بيشوفش غير 'published' (انظر policy في §4).
-- 'pending' = البوت تولّده ومستنى فحص، 'rejected' = مرفوض ويظل للتاريخ.
ALTER TABLE public.questions
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'published';

-- وقت ما السؤال بقى منشور فعلياً (مش وقت الإدراج) — ده اللي بنبني عليه
-- «الأحدث أولاً» في التطبيق وفي الـindex.
ALTER TABLE public.questions
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ
        NOT NULL DEFAULT timezone('utc'::text, now());

-- سبب الرفض/ملاحظة الفحص — بتسهّل مراجعة أسباب توقف البوت.
ALTER TABLE public.questions
    ADD COLUMN IF NOT EXISTS review_note TEXT;

-- عدد المرات اللي السؤال اتلعب فيها: بيسمح للبوت يتجنّب الأسئلة المستنزفة
-- وللتحليل «أنهي الأسئلة بتتخطى». اختياري — التطبيق ما بيكتبهاش الآن.
ALTER TABLE public.questions
    ADD COLUMN IF NOT EXISTS times_played INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN public.questions.content_hash IS
    'بصمة sha256 للمحتوى الموحّد — تُستخدم لمنع التكرار على مستوى القاعدة';
COMMENT ON COLUMN public.questions.status IS
    'pending | published | rejected — التطبيق يقرأ published فقط';
COMMENT ON COLUMN public.questions.source IS
    'seed | bot | curated | admin';

-- ---------------------------------------------------------------------------
-- 2) قيود السلامة — تمنع المحتوى الناقص من الدخول حتى لو البوت غلط
-- ---------------------------------------------------------------------------
-- القيود على الأعمدة **القائمة من V1** (game_type/difficulty/question/indices)
-- بتتضاف NOT VALID: لو أي صف قديم مخالف، ALTER كان هيفشل ويوقف الملف كله.
-- NOT VALID بيمنع المخالف الجديد وبس، والصيانة اللاحقة تعمل VALIDATE.
-- قيود الأعمدة الجديدة (status/source) عادية لأن القيم الافتراضية مضمونة.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'questions_status_check' AND conrelid = 'public.questions'::regclass
    ) THEN
        ALTER TABLE public.questions
            ADD CONSTRAINT questions_status_check
            CHECK (status IN ('pending', 'published', 'rejected'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'questions_source_check' AND conrelid = 'public.questions'::regclass
    ) THEN
        ALTER TABLE public.questions
            ADD CONSTRAINT questions_source_check
            CHECK (source IN ('seed', 'bot', 'curated', 'admin'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'questions_game_type_check' AND conrelid = 'public.questions'::regclass
    ) THEN
        -- التطابق مع قيم OfflineQuestionEntity category + قيم V1
        ALTER TABLE public.questions
            ADD CONSTRAINT questions_game_type_check
            CHECK (game_type IN (
                'thirty_seconds', 'sabaho', 'true_false', 'pvp',
                'event_ordering', 'friendly'
            ))
            NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'questions_difficulty_check' AND conrelid = 'public.questions'::regclass
    ) THEN
        -- لازم تطابق Difficulty enum في Kotlin: EASY/MEDIUM/HARD
        ALTER TABLE public.questions
            ADD CONSTRAINT questions_difficulty_check
            CHECK (difficulty IS NULL OR difficulty IN ('easy', 'medium', 'hard'))
            NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'questions_index_sanity' AND conrelid = 'public.questions'::regclass
    ) THEN
        -- correct_index داخل حدود الخيارات دايماً (الـseed الحالي سليم)
        ALTER TABLE public.questions
            ADD CONSTRAINT questions_index_sanity
            CHECK (
                correct_index >= 0
                AND correct_index < GREATEST(jsonb_array_length(options), 1)
            )
            NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'questions_text_not_blank' AND conrelid = 'public.questions'::regclass
    ) THEN
        ALTER TABLE public.questions
            ADD CONSTRAINT questions_text_not_blank
            CHECK (length(btrim(question)) >= 8)
            NOT VALID;
    END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- 3) الفهارس — كلها IF NOT EXISTS، واثنتين منهم اليونيكون اللي عليهما الاتّكال
-- ---------------------------------------------------------------------------

-- اليونيكون ده هو «مانع التكرار» الحقيقي. partial عشان يسمح بأكثر من null
-- (الـseed القديم مفيهوش hash) ومابيفرضش إعادة كتابة صفوف قديمة.
CREATE UNIQUE INDEX IF NOT EXISTS questions_content_hash_uidx
    ON public.questions (content_hash)
    WHERE content_hash IS NOT NULL;

-- قراءة التطبيق: «أسئلة النوع دي، المنشورة، الأحدث الأول».
CREATE INDEX IF NOT EXISTS questions_feed_idx
    ON public.questions (game_type, status, published_at DESC);

-- عدادات البوت: كام سؤال اتضاف النهاردة (الحد اليومي).
CREATE INDEX IF NOT EXISTS questions_created_at_idx
    ON public.questions (created_at DESC);

-- الفهرس على نص السؤال لمقاربة التشابه وقت التدقيق (اختياري للبوت المحلي).
--
-- الترتيب مهم: CREATE INDEX على gin_trgm_ops بيرمي خطأ مباشرةً لو الامتداد
-- مش مركّب، وخطأ واحد في SQL Editor بيوقف باقي الملف. فالفهرس كله لازم
-- يكون **جوه** الشرط مش بعده.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS questions_question_trgm_idx
                 ON public.questions USING gin (lower(question) gin_trgm_ops)';
        RAISE NOTICE 'فهرس التشابه اتعمل ✓ (pg_trgm)';
    ELSE
        RAISE NOTICE 'pg_trgm غير مُفعّل — اتخطينا فهرس التشابه (مش هيأثر على اللعب). شغّله بـ: CREATE EXTENSION IF NOT EXISTS pg_trgm;';
    END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- 4) الأمان — التطبيق يقرأ المنشور فقط، والكتابة للبوت وحدُه
-- ---------------------------------------------------------------------------

ALTER TABLE public.questions ENABLE ROW LEVEL SECURITY;

-- لا FORCE ROW LEVEL SECURITY هنا: FORCE بيخضع حتى مالك الجدول (postgres)
-- للسياسات، فأي صيانة يدوية من SQL Editor ترجع صفوف صامتة بدون خطأ —
-- وده أصعب حاجة تتشخّص. service_role بيفلت من RLS بسياسة Supabase
-- الافتراضية، فمنع القراءة للأنون متضمّن من غير FORCE.

-- سياسة V1 كانت USING (true) على كل الجدول؛ بنستبدلها بسياسة تفلتر بالحالة
-- عشان أسئلة 'pending' المولّدة ما توصلش للاعيبين قبل الفحص.
DROP POLICY IF EXISTS "Public read questions" ON public.questions;
DROP POLICY IF EXISTS questions_public_read ON public.questions;

CREATE POLICY questions_public_read ON public.questions
    FOR SELECT
    TO anon, authenticated
    USING (status = 'published');

-- مفيش policy لإدراج/تحديث لـ anon: مفتاح Anon العام مابيقدرش يكتب خالص.
-- البوت بيستخدم service_role ( بيتجاوز RLS) المخزّن في GitHub Secrets.

-- إعادة تأكيد الأذونات (GRANT منفصل عن RLS والاتنين لازم يكونوا مضبوطين).
REVOKE ALL ON public.questions FROM anon, authenticated;
GRANT SELECT ON public.questions TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.questions TO service_role;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO service_role;

-- ---------------------------------------------------------------------------
-- 5) view القراءة بتاع التطبيق — اسم ثابت مش مرتبط بتفاصيل الأعمدة
-- ---------------------------------------------------------------------------
-- التطبيق بيستعلم من questions_feed مش من questions مباشرةً: كده أي عمود
-- داخلي (review_note / times_played) ما بيتسرّش للعميل، وأي تغيير في الجدول
-- ما بيكسرش العميل. لازم DROP قبل CREATE لأن الأعمدة بتغيّرت.

DROP VIEW IF EXISTS public.questions_feed;
CREATE VIEW public.questions_feed AS
SELECT
    q.id,
    q.game_type,
    q.question,
    q.options,
    q.correct_index,
    -- true_false مفيهاش خيارات في التطبيق: بنولّدها هنا عشان العميل
    -- يستعلم بمنطق واحد لكل الأنواع
    CASE
        WHEN q.game_type = 'true_false' THEN 1
        ELSE jsonb_array_length(q.options)
    END AS option_count,
    COALESCE(q.difficulty, 'medium') AS difficulty,
    COALESCE(q.category, 'عامة')     AS category,
    COALESCE(q.explanation, '')      AS explanation,
    q.source,
    q.published_at,
    q.created_at
FROM public.questions q
WHERE q.status = 'published'
  -- بنستبعد أي سؤال ناقص إجابة صحيحة صالحة، فالحارس هنا مش في العميل
  AND q.correct_index >= 0
  AND (
      q.game_type = 'true_false'
      OR (jsonb_array_length(q.options) >= 2 AND q.correct_index < jsonb_array_length(q.options))
  );

COMMENT ON VIEW public.questions_feed IS
    'قراءة فقط: الأسئلة المنشورة الصالحة للعب، مرتّبة زمنياً في استعلام العميل';

GRANT SELECT ON public.questions_feed TO anon, authenticated;
GRANT ALL ON public.questions_feed TO service_role;

-- عدّادات للمراقبة (بتستعملها في خطوة ملخّص الـworkflow لو حبيت).
DROP VIEW IF EXISTS public.questions_stats;
CREATE VIEW public.questions_stats AS
SELECT
    q.game_type,
    q.status,
    q.source,
    COUNT(*)                    AS total,
    MAX(q.published_at)         AS last_published_at,
    COUNT(*) FILTER (
        WHERE q.published_at >= timezone('utc'::text, now()) - INTERVAL '24 hours'
    )                           AS published_last_24h
FROM public.questions q
GROUP BY q.game_type, q.status, q.source
ORDER BY q.game_type, q.status, q.source;

GRANT SELECT ON public.questions_stats TO anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 6) أسئلة curated أولية — آمنة ومراجَعة، ومطابقة لقواعد التدقيق بتاعة البوت
-- ---------------------------------------------------------------------------
-- id ثابت بصيغة curated_v3_* عشان الـseed idempotent بمبدأ WHERE NOT EXISTS
-- (مفيش يونيكون على id يمنع إعادة التشغيل بعد أول مرة؟ فيه PRIMARY KEY، بس
-- بنستخدم NOT EXISTS عشان رسالة أوضح ومش كسر لو اتنفذ مرتين).

INSERT INTO public.questions
    (id, game_type, question, options, correct_index, difficulty, category, explanation,
     content_hash, source, status, published_at)
SELECT
    v.id, v.game_type, v.question, v.options::jsonb, v.correct_index, v.difficulty,
    v.category, v.explanation,
    -- NULL عمدًا، مش md5: البوت بيحسب sha256 على النص **الموحّد** (بلا تشكيل/
    -- بهمزات موحّدة) ومطابقته هي عقد إزالة التكرار. بصمة md5 بحساب مختلف
    -- كانت هتملأ العمود بقيم مابتطابقش أي حساب في التطبيق، فالبوت يعيد نشر
    -- نفس السؤال وهو شايف إنه جديد — أسوأ من عمود فاضي.
    -- فهرس content_hash الجزئي (WHERE ... IS NOT NULL) بيخلي NULL قانوني.
    NULL,
    'curated', 'published', timezone('utc'::text, now())
FROM (VALUES
    (
        'curated_v3_ts_01', 'thirty_seconds',
        'مين كسب كأس العالم 2022 في قطر؟',
        '["الأرجنتين","فرنسا","البرازيل","كرواتيا"]', 0, 'easy', 'كأس العالم',
        'الأرجنتين فازت على فرنسا بركلات الترجيح في النهائي.',
        'الأرجنتين'
    ),
    (
        'curated_v3_ts_02', 'thirty_seconds',
        'كام لاعب في الفريق الواحد داخل الملعب في كرة القدم؟',
        '["11 لاعب","10 لاعبين","12 لاعب","9 لاعبين"]', 0, 'easy', 'قوانين',
        'الفريق بينزل بـ 11 لاعب منهم حارس المرمى.',
        '11 لاعب'
    ),
    (
        'curated_v3_ts_03', 'thirty_seconds',
        'أنهي بلد استضافت كأس العالم 2018؟',
        '["روسيا","قطر","البرازيل","جنوب أفريقيا"]', 0, 'medium', 'كأس العالم',
        'استضافت روسيا نسخة 2018 وكسبتها فرنسا.',
        'روسيا'
    ),
    (
        'curated_v3_tf_01', 'true_false',
        'كأس العالم بيتلعب كل أربع سنين.',
        '["صح","غلط"]', 0, 'easy', 'بطولات',
        'النسخة بتتعمل كل أربع سنين من 1930 ما عدا فترات الحرب العالمية.',
        'صح'
    ),
    (
        'curated_v3_tf_02', 'true_false',
        'عدد اللاعبين في فريق كرة اليد هو 11 لاعب.',
        '["صح","غلط"]', 1, 'medium', 'قوانين',
        'كرة اليد بتلعب بـ 7 لاعبين لكل فريق، وكرة القدم هي اللي 11.',
        'غلط'
    ),
    (
        'curated_v3_pvp_01', 'pvp',
        'مين سجّل هدف نهائي دوري أبطال أوروبا 2022 بين ريال مدريد وليفربول؟',
        '["فينيسيوس جونيور","كريم بنزيما","محمد صلاح","سيرجيو راموس"]', 0, 'hard', '',
        'فينيسيوس جونيور سجّل هدف الفوز في المباراة اللي انتّهت 1-0.',
        'فينيسيوس جونيور'
    ),
    (
        'curated_v3_sb_01', 'sabaho',
        'أنهي نادٍ كسب دوري أبطال أوروبا أكثر من أي نادٍ تاني؟',
        '["ريال مدريد","ميلان","ليفربول","بايرن ميونخ"]', 0, 'medium', 'بطولات',
        'ريال مدريد هو الأكثر تتويجاً بالبطولة الأوروبية.',
        'ريال مدريد'
    )
) AS v(
    id, game_type, question, options, correct_index, difficulty, category, explanation, correct_answer
)
WHERE NOT EXISTS (SELECT 1 FROM public.questions q WHERE q.id = v.id);

-- ---------------------------------------------------------------------------
-- 7) Realtime — التطبيق يقدر يشترك في إضافة أسئلة جديدة
-- ---------------------------------------------------------------------------
-- بيخلّي الـpublication يشمل الجدول، فلو حبيت تدفع «فيه سؤال جديد» للتطبيق
-- لحظياً بدل الانتظار لحد التحديث الدوري، نفس عميل Realtime بتاع الغرف
-- (SupabaseRealtimeClient) يقدر يشترك عليه بتغيير الجدول بس.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_extension WHERE extname = 'pg_net')
       OR EXISTS (SELECT 1 FROM information_schema.tables
                  WHERE table_schema = 'supabase_realtime' AND table_name = 'publication') THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_publication_tables
            WHERE pubname = 'supabase_realtime'
              AND schemaname = 'public'
              AND tablename = 'questions'
        ) THEN
            ALTER PUBLICATION supabase_realtime ADD TABLE public.questions;
        END IF;
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'ماتقدرناش نعدّل الـpublication الآن (طبيعي لو الـpublication مش موجود)';
END
$$;

-- إعادة تحميل كاش الـschema في PostgREST عشان الأعمدة/الـviews الجديدة تظهر فوراً
NOTIFY pgrst, 'reload schema';

-- ===========================================================================
-- نهاية V3
-- ===========================================================================
-- للتحقق بعد التنفيذ:
--   SELECT game_type, status, COUNT(*) FROM public.questions GROUP BY 1,2;
--   SELECT * FROM public.questions_stats;
--   SELECT id, game_type, question FROM public.questions_feed
--     WHERE game_type = 'thirty_seconds' ORDER BY published_at DESC LIMIT 5;
-- ===========================================================================
