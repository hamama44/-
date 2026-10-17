-- =====================================================================
-- ملعب التحدي (Malab Al-Tahaddi) - Supabase Database Schema
-- يدعم تخزين الأسئلة، اللاعبين، لوحة الصدارة العالمية، والغرف الأونلاين المباشرة
-- =====================================================================

-- 1. جدول الأسئلة الكروية (Questions Table)
CREATE TABLE IF NOT EXISTS public.questions (
    id TEXT PRIMARY KEY,
    game_type TEXT NOT NULL, -- 'thirty_seconds', 'sabaho', 'true_false', 'event_ordering'
    question TEXT NOT NULL,
    options JSONB NOT NULL DEFAULT '[]'::jsonb,
    correct_index INTEGER NOT NULL DEFAULT 0,
    difficulty TEXT DEFAULT 'medium', -- 'easy', 'medium', 'hard'
    category TEXT DEFAULT 'عامة',
    explanation TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 2. جدول نجوم ولاعبي كرة القدم (Players Table for Mystery & Career Challenges)
CREATE TABLE IF NOT EXISTS public.players (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    nationality TEXT,
    current_club TEXT,
    position TEXT,
    shirt_number INTEGER,
    hints JSONB DEFAULT '[]'::jsonb,
    career_history JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 3. جدول النتائج ولوحة الشرف العالمية (Scores & Leaderboard Table)
CREATE TABLE IF NOT EXISTS public.scores (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    player_name TEXT NOT NULL,
    points INTEGER DEFAULT 0 NOT NULL,
    coins INTEGER DEFAULT 0 NOT NULL,
    level_title TEXT DEFAULT 'مشجع هاوٍ',
    favorite_club TEXT DEFAULT 'ريال مدريد',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 4. جدول غرف اللعب المباشر أونلاين (Online Multiplayer Game Rooms)
CREATE TABLE IF NOT EXISTS public.game_rooms (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    room_code TEXT UNIQUE NOT NULL, -- e.g. 'STAD77'
    game_type TEXT NOT NULL DEFAULT 'thirty_seconds', -- 'thirty_seconds', 'buzzer', 'tictactoe', 'auction'
    status TEXT NOT NULL DEFAULT 'waiting', -- 'waiting', 'playing', 'finished'
    current_round INTEGER DEFAULT 1,
    max_rounds INTEGER DEFAULT 5,
    host_name TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 5. جدول لاعبي الغرفة الأونلاين (Room Players)
CREATE TABLE IF NOT EXISTS public.room_players (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    room_code TEXT NOT NULL REFERENCES public.game_rooms(room_code) ON DELETE CASCADE,
    player_name TEXT NOT NULL,
    team_color TEXT DEFAULT 'RED', -- 'RED', 'BLUE'
    score INTEGER DEFAULT 0,
    is_ready BOOLEAN DEFAULT false,
    is_host BOOLEAN DEFAULT false,
    last_ping TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Enable Row Level Security (RLS)
ALTER TABLE public.questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.players ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.scores ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.game_rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_players ENABLE ROW LEVEL SECURITY;

-- Allow public read & write access for gaming clients
CREATE POLICY "Public read questions" ON public.questions FOR SELECT USING (true);
CREATE POLICY "Public read players" ON public.players FOR SELECT USING (true);
CREATE POLICY "Public read scores" ON public.scores FOR SELECT USING (true);
CREATE POLICY "Public insert scores" ON public.scores FOR INSERT WITH CHECK (true);

CREATE POLICY "Public read game_rooms" ON public.game_rooms FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Public read room_players" ON public.room_players FOR ALL USING (true) WITH CHECK (true);

-- Enable Realtime publication for online rooms and scores
BEGIN;
  DROP PUBLICATION IF EXISTS supabase_realtime;
  CREATE PUBLICATION supabase_realtime FOR TABLE public.game_rooms, public.room_players, public.scores;
COMMIT;

-- =====================================================================
-- Initial Seed Data (بيانات أولية بالعربية)
-- =====================================================================

INSERT INTO public.questions (id, game_type, question, options, correct_index, difficulty, category, explanation) VALUES
('q1', 'thirty_seconds', 'من هو الهداف التاريخي لكأس العالم؟', '["ميروسلاف كلوزه", "رونالدو البرازيلي", "ليونيل ميسي", "بيليه"]', 0, 'easy', 'كأس العالم', 'كلوزه برصيد 16 هدفاً مونديالياً.'),
('q2', 'thirty_seconds', 'كم عدد كرات الذهب التي فاز بها ليونيل ميسي؟', '["8 كرات", "7 كرات", "5 كرات", "6 كرات"]', 0, 'easy', 'جوائز', 'ميسي حقق 8 كرات ذهبية تاريخية.'),
('q3', 'sabaho', 'من قاد منتخب المغرب لنصف نهائي مونديال 2022؟', '["وليد الركراكي", "هيرفي رينارد", "بادو الزاكي", "وحيد خليلوزيتش"]', 0, 'medium', 'المونديال العربي', 'وليد الركراكي رأس لافوكا الأسطوري.'),
('q4', 'sabaho', 'أي نادٍ فاز بدوري أبطال إفريقيا 2024؟', '["الأهلي المصري", "الترجي التونسي", "صن داونز", "الوداد"]', 0, 'medium', 'إفريقيا', 'الأهلي المصري للمرة الثانية عشرة.'),
('q5', 'true_false', 'هل حقق كريستيانو رونالدو لقب أمم أوروبا 2016 مع البرتغال؟', '["صح", "غلط"]', 0, 'easy', 'يورو', 'نعم حقق اللقب بعد الفوز على فرنسا 1-0.')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.scores (player_name, points, coins, level_title, favorite_club) VALUES
('عمر المدريدي 👑', 4820, 950, 'أسطورة الملاعب', 'ريال مدريد'),
('ياسين الأطلسي 🇲🇦', 4350, 820, 'محترف دولي', 'الوداد البيضاوي'),
('أحمد الفراعنة 🇪🇬', 3910, 780, 'كابتن الملعب', 'الأهلي المصري');
