-- =====================================================================
-- ملعب التحدي (Malab Al-Tahaddi) — Supabase Schema V2
-- جداول/واجهات: Users • Rooms • Leaderboard
-- =====================================================================
-- هذا الملف "توسعة" للمخطط الأساسي في supabase_schema.sql وليس بديلاً عنه.
-- الاستراتيجية: الإبقاء على الجداول الحقيقية الحالية
--   (public.game_rooms, public.room_players, public.scores)
-- وإضافة الأعمدة الناقصة إليها، ثم تعريف VIEWS بالأسماء المطلوبة
--   (public.users, public.rooms, public.room_participants, public.leaderboard)
-- حتى يقرأ ويكتب التطبيق عبر أسماء موحّدة بدون تكرار البيانات.
--
-- طريقة التشغيل: افتح Supabase Dashboard → SQL Editor → الصق هذا الملف كاملاً → Run.
-- كل الأوامر هنا idempotent (قابلة لإعادة التشغيل بأمان) ما عدا ملاحظة واحدة
-- عند DROP VIEW (مذكورة في مكانها).
--
-- ملاحظات مهمة:
-- 1) الـ Views هنا "Automatically Updatable" (جدول واحد في FROM، بدون تجميع)
--    لذلك INSERT / UPDATE / DELETE تعمل عليها تماماً مثل الجداول، ما عدا
--    public.users و public.user_challenges فهي للقراءة فقط (فيها GROUP BY / Window).
-- 2) الـ View في PostgreSQL يُنفَّذ بصلاحيات مالكه (postgres) أي أنه يتجاوز RLS
--    للجداول الأساسية. الجداول الأساسية لديها سياسات عامة (USING true) أصلاً،
--    لذلك السلوك مقصود: عملاء اللعبة يقرؤون ويكتبون بالمفتاح anon.
-- 3) Supabase Realtime لا يدعم الـ Views، لذا تبقى الـ publication على الجداول
--    الأساسية game_rooms / room_players / scores كما هي في المخطط الأساسي.
-- =====================================================================


-- =====================================================================
-- 1) توسيع الجداول الحالية بأعمدة ناقصة (ADD COLUMN IF NOT EXISTS)
-- =====================================================================

-- 1.1 غرف اللعب (Rooms) — سعة الغرفة والخصوصية والمنطقة
ALTER TABLE public.game_rooms
    ADD COLUMN IF NOT EXISTS max_players INTEGER NOT NULL DEFAULT 6,
    ADD COLUMN IF NOT EXISTS is_private  BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS region      TEXT    NOT NULL DEFAULT 'MENA';

COMMENT ON COLUMN public.game_rooms.max_players IS 'أقصى عدد لاعبين مسموح في الغرفة (2 - 12)';
COMMENT ON COLUMN public.game_rooms.is_private  IS 'غرفة خاصة بالكود فقط ولا تظهر في القائمة العامة';
COMMENT ON COLUMN public.game_rooms.region      IS 'السيرفر الإقليمي: MENA, GULF, NAfrica, EU';

-- 1.2 لاعبو الغرفة (Participants) — الأفاتار ولحظة الانضمام
ALTER TABLE public.room_players
    ADD COLUMN IF NOT EXISTS avatar_emoji TEXT NOT NULL DEFAULT '👤',
    ADD COLUMN IF NOT EXISTS joined_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT timezone('utc'::text, now());

COMMENT ON COLUMN public.room_players.avatar_emoji IS 'إيموجي الأفاتار الظاهر في قائمة المشاركين';
COMMENT ON COLUMN public.room_players.joined_at    IS 'توقيت الانضمام الفعلي (last_ping يتحدث مع كل نبضة اتصال)';

-- 1.3 لوحة الصدارة (Leaderboard) — نوع التحدي لكل نتيجة
ALTER TABLE public.scores
    ADD COLUMN IF NOT EXISTS challenge_type TEXT NOT NULL DEFAULT 'general';

COMMENT ON COLUMN public.scores.challenge_type IS
    'نوع التحدي: general, thirty_seconds, sabaho, buzzer, tictactoe, auction, party';


-- =====================================================================
-- 2) فهارس الأداء (Indexes)
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_game_rooms_status_created
    ON public.game_rooms (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_game_rooms_region_status
    ON public.game_rooms (region, status);
CREATE INDEX IF NOT EXISTS idx_room_players_room_joined
    ON public.room_players (room_code, joined_at);
CREATE INDEX IF NOT EXISTS idx_room_players_room_name
    ON public.room_players (room_code, player_name);
CREATE INDEX IF NOT EXISTS idx_scores_points_desc
    ON public.scores (points DESC);
CREATE INDEX IF NOT EXISTS idx_scores_player_created
    ON public.scores (player_name, created_at DESC);


-- =====================================================================
-- 3) تحديث updated_at تلقائياً عند تعديل الغرفة
-- =====================================================================
CREATE OR REPLACE FUNCTION public.touch_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := timezone('utc'::text, now());
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_game_rooms_touch_updated_at ON public.game_rooms;
CREATE TRIGGER trg_game_rooms_touch_updated_at
    BEFORE UPDATE ON public.game_rooms
    FOR EACH ROW
    EXECUTE FUNCTION public.touch_updated_at();


-- =====================================================================
-- 4) الـ VIEWS بالأسماء المطلوبة: Rooms / Users / Leaderboard
-- =====================================================================
-- ملاحظة: DROP VIEW IF EXISTS ضروري هنا لأن CREATE OR REPLACE VIEW يفشل
-- إذا تغيّرت قائمة الأعمدة. الصلاحيات (GRANT) تُعاد مباشرة بعد الإنشاء.
-- =====================================================================

-- 4.1 Rooms → واجهة قابلة للكتابة فوق public.game_rooms
DROP VIEW IF EXISTS public.rooms;
CREATE VIEW public.rooms AS
SELECT
    gr.id,
    gr.room_code,
    gr.game_type,
    gr.status,
    gr.current_round,
    gr.max_rounds,
    gr.max_players,
    gr.is_private,
    gr.region,
    gr.host_name,
    gr.created_at,
    gr.updated_at
FROM public.game_rooms AS gr;

COMMENT ON VIEW public.rooms IS
    'غرف اللعب الأونلاين (قابل للقراءة والكتابة) — الطبقة الموحّدة فوق game_rooms';

-- 4.2 Room Participants → واجهة قابلة للكتابة فوق public.room_players
DROP VIEW IF EXISTS public.room_participants;
CREATE VIEW public.room_participants AS
SELECT
    rp.id,
    rp.room_code,
    rp.player_name,
    rp.avatar_emoji,
    rp.team_color,
    rp.score,
    rp.is_ready,
    rp.is_host,
    rp.joined_at,
    rp.last_ping
FROM public.room_players AS rp;

COMMENT ON VIEW public.room_participants IS
    'المشاركون داخل كل غرفة (قابل للقراءة والكتابة) — فوق room_players';

-- 4.2b Rooms Overview → للقراءة فقط: الغرف مع عدد المشاركين والجاهزين
--      تُستخدم في قوائم الغرف (SupabaseRoomsDao.fetchRooms) لأن view الكتابة
--      `public.rooms` يجب أن يبقى جدولاً واحداً حتى يظل قابلاً للتحديث.
DROP VIEW IF EXISTS public.rooms_overview;
CREATE VIEW public.rooms_overview AS
SELECT
    gr.id,
    gr.room_code,
    gr.game_type,
    gr.status,
    gr.current_round,
    gr.max_rounds,
    gr.max_players,
    gr.is_private,
    gr.region,
    gr.host_name,
    gr.created_at,
    gr.updated_at,
    COUNT(rp.id)::INTEGER                                  AS participant_count,
    COALESCE(SUM(CASE WHEN rp.is_ready THEN 1 ELSE 0 END), 0)::INTEGER AS ready_count
FROM public.game_rooms AS gr
LEFT JOIN public.room_players AS rp ON rp.room_code = gr.room_code
GROUP BY gr.id;

COMMENT ON VIEW public.rooms_overview IS
    'قائمة الغرف مع عدد المشاركين والجاهزين (للقراءة فقط) — GROUP BY على المفتاح الأساسي';

-- 4.3 Leaderboard → واجهة قابلة للكتابة فوق public.scores
DROP VIEW IF EXISTS public.leaderboard;
CREATE VIEW public.leaderboard AS
SELECT
    s.id,
    s.player_name,
    s.points,
    s.coins,
    s.level_title,
    s.favorite_club,
    s.challenge_type,
    s.created_at
FROM public.scores AS s;

COMMENT ON VIEW public.leaderboard IS
    'لوحة الصدارة العالمية (قابل للقراءة والكتابة) — فوق scores';

-- 4.4 Users → تجميع للقراءة فقط: صف واحد لكل لاعب مع إجمالي تحدياته
DROP VIEW IF EXISTS public.users;
CREATE VIEW public.users AS
WITH ranked AS (
    SELECT
        s.player_name,
        s.level_title,
        s.favorite_club,
        s.created_at,
        ROW_NUMBER() OVER (
            PARTITION BY s.player_name
            ORDER BY s.created_at DESC, s.points DESC
        ) AS rn
    FROM public.scores AS s
),
latest AS (
    SELECT player_name, level_title, favorite_club, created_at
    FROM ranked
    WHERE rn = 1
),
aggregated AS (
    SELECT
        s.player_name,
        SUM(s.points)::INTEGER          AS total_points,
        SUM(s.coins)::INTEGER           AS total_coins,
        COUNT(*)::INTEGER               AS challenges_played,
        MAX(s.points)::INTEGER          AS best_challenge_points,
        MIN(s.created_at)               AS joined_at,
        MAX(s.created_at)               AS last_seen_at
    FROM public.scores AS s
    GROUP BY s.player_name
)
SELECT
    md5(latest.player_name)             AS id,
    latest.player_name                  AS display_name,
    latest.level_title                  AS level_title,
    latest.favorite_club                AS favorite_club,
    aggregated.total_points             AS total_points,
    aggregated.total_coins              AS total_coins,
    aggregated.challenges_played        AS challenges_played,
    aggregated.best_challenge_points    AS best_challenge_points,
    aggregated.joined_at                AS joined_at,
    aggregated.last_seen_at             AS last_seen_at
FROM latest
JOIN aggregated ON aggregated.player_name = latest.player_name;

COMMENT ON VIEW public.users IS
    'ملف لكل لاعب (للقراءة فقط): يُبنى تلقائياً من نتائج leaderboard. '
    'لإضافة لاعب جديد أدرج أول نتيجة له في public.leaderboard (انظر SupabaseUserDao.addPlayer).';

-- 4.5 User Challenges → كل تحدٍ خاضه اللاعب (للقراءة فقط)
DROP VIEW IF EXISTS public.user_challenges;
CREATE VIEW public.user_challenges AS
SELECT
    s.id                            AS challenge_id,
    md5(s.player_name)              AS user_id,
    s.player_name                   AS display_name,
    s.challenge_type                AS challenge_type,
    s.points                        AS points,
    s.coins                         AS coins,
    s.level_title                   AS level_title,
    s.favorite_club                 AS favorite_club,
    s.created_at                    AS played_at
FROM public.scores AS s;

COMMENT ON VIEW public.user_challenges IS
    'سجل تحديات اللاعبين (للقراءة فقط) — صف لكل نتيجة في scores';


-- =====================================================================
-- 5) الصلاحيات (Grants) حتى يظهر كل view عبر PostgREST
-- =====================================================================
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.rooms             TO anon, authenticated, service_role;
GRANT SELECT                          ON public.rooms_overview   TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.room_participants TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.leaderboard       TO anon, authenticated, service_role;
GRANT SELECT                          ON public.users            TO anon, authenticated, service_role;
GRANT SELECT                          ON public.user_challenges  TO anon, authenticated, service_role;

-- صلاحيات الجداول الأساسية (للقراءة المباشرة والـ Realtime)
GRANT SELECT, INSERT, UPDATE, DELETE ON public.game_rooms   TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.room_players TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.scores       TO anon, authenticated, service_role;
GRANT SELECT                          ON public.questions   TO anon, authenticated, service_role;
GRANT SELECT                          ON public.players     TO anon, authenticated, service_role;


-- =====================================================================
-- 6) Row Level Security: تأكيد السياسات العامة على الجداول الأساسية
--    (الـ Views تتجاوز RLS لأنها مملوكة لـ postgres، لكن نبقي السياسات
--     صريحة حتى تعمل القراءة المباشرة من الجداول ومن Realtime بشكل سليم)
-- =====================================================================
ALTER TABLE public.game_rooms   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_players ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.scores       ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public read game_rooms" ON public.game_rooms;
CREATE POLICY "Public read game_rooms" ON public.game_rooms
    FOR ALL USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Public read room_players" ON public.room_players;
CREATE POLICY "Public read room_players" ON public.room_players
    FOR ALL USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Public read scores" ON public.scores;
CREATE POLICY "Public read scores" ON public.scores FOR SELECT USING (true);

DROP POLICY IF EXISTS "Public insert scores" ON public.scores;
CREATE POLICY "Public insert scores" ON public.scores FOR INSERT WITH CHECK (true);

DROP POLICY IF EXISTS "Public update scores" ON public.scores;
CREATE POLICY "Public update scores" ON public.scores FOR UPDATE USING (true) WITH CHECK (true);


-- =====================================================================
-- 7) Realtime: الجداول الأساسية فقط (الـ Views غير مدعومة)
-- =====================================================================
BEGIN;
    DROP PUBLICATION IF EXISTS supabase_realtime;
    CREATE PUBLICATION supabase_realtime
        FOR TABLE public.game_rooms, public.room_players, public.scores;
COMMIT;


-- =====================================================================
-- 8) بيانات أولية تجريبية (Seed) — آمنة عند إعادة التشغيل
-- =====================================================================
INSERT INTO public.game_rooms (room_code, game_type, status, current_round, max_rounds,
                               max_players, is_private, region, host_name)
VALUES
    ('STAD77', 'thirty_seconds', 'waiting', 1, 5, 6,  false, 'MENA',   'كابتن ساري ⚽'),
    ('BUZZ22', 'buzzer',         'waiting', 1, 3, 4,  false, 'GULF',   'فارس الكالتشيو 🇮🇹'),
    ('GRID33', 'tictactoe',      'playing', 2, 5, 2,  true,  'NAfrica','البروفيسور 🧠')
ON CONFLICT (room_code) DO NOTHING;

-- ملاحظة: room_players و scores ليس لديهما قيد فريد على الاسم، لذلك نستخدم
-- NOT EXISTS بدل ON CONFLICT DO NOTHING حتى لا تتكرر الصفوف عند إعادة التشغيل.
INSERT INTO public.room_players (room_code, player_name, avatar_emoji, team_color, score, is_ready, is_host)
SELECT v.room_code, v.player_name, v.avatar_emoji, v.team_color, v.score, v.is_ready, v.is_host
FROM (
    VALUES
        ('STAD77', 'كابتن ساري ⚽',      '👑', 'RED',  0,  true,  true),
        ('STAD77', 'عمر المدريدي',       '⚪', 'BLUE', 0,  false, false),
        ('BUZZ22', 'فارس الكالتشيو 🇮🇹', '🛡️', 'RED',  0,  true,  true),
        ('GRID33', 'البروفيسور 🧠',      '🧠', 'RED',  40, true,  true),
        ('GRID33', 'سارة الذكية',        '🌟', 'BLUE', 30, true,  false)
) AS v(room_code, player_name, avatar_emoji, team_color, score, is_ready, is_host)
WHERE NOT EXISTS (
    SELECT 1 FROM public.room_players rp
    WHERE rp.room_code = v.room_code AND rp.player_name = v.player_name
);

INSERT INTO public.scores (player_name, points, coins, level_title, favorite_club, challenge_type)
SELECT v.player_name, v.points, v.coins, v.level_title, v.favorite_club, v.challenge_type
FROM (
    VALUES
        ('عمر المدريدي 👑',   4820, 950, 'أسطورة الملاعب', 'ريال مدريد',      'thirty_seconds'),
        ('ياسين الأطلسي 🇲🇦', 4350, 820, 'محترف دولي',     'الوداد البيضاوي', 'sabaho'),
        ('أحمد الفراعنة 🇪🇬', 3910, 780, 'كابتن الملعب',   'الأهلي المصري',   'buzzer'),
        ('سارة الذكية 🌟',    3120, 640, 'محترفة الصالون', 'الهلال السعودي',  'party')
) AS v(player_name, points, coins, level_title, favorite_club, challenge_type)
WHERE NOT EXISTS (
    SELECT 1 FROM public.scores s WHERE s.player_name = v.player_name
);


-- =====================================================================
-- 9) تحديث كاش المخطط في PostgREST حتى تظهر الـ Views فوراً
-- =====================================================================
NOTIFY pgrst, 'reload schema';


-- =====================================================================
-- 10) أمثلة استعلامات سريعة للتحقق بعد التشغيل
-- =====================================================================
-- SELECT * FROM public.rooms ORDER BY created_at DESC LIMIT 20;
-- SELECT * FROM public.room_participants WHERE room_code = 'STAD77' ORDER BY joined_at;
-- SELECT * FROM public.users ORDER BY total_points DESC;
-- SELECT * FROM public.leaderboard ORDER BY points DESC LIMIT 25;
--
-- إضافة لاعب جديد (وهو ما تفعله دالة Kotlin: addNewPlayer):
-- INSERT INTO public.leaderboard (player_name, points, coins, level_title, favorite_club, challenge_type)
-- VALUES ('لاعب جديد 🎮', 0, 0, 'مشجع هاوٍ', 'ريال مدريد', 'general');
-- =====================================================================
