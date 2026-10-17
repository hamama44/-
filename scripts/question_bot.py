#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ملعب التحدي — Question Bot V3
=============================

يولّد أسئلة كروية جديدة بالعربي (من Gemini أو من ملف curated)، يدقّقها،
يمنع التكرار، وينشرها في جدول public/questions في Supabase.

مصمّم يشتغل في GitHub Actions بـ Python أساسي فقط (بدون pip install):
كل الاستيراد هنا من المكتبة القياسية، عشان الـrunner بيلاقي كل حاجة جاهزة.

مبدأ أساسي: **الحذف أأمن من الإضافة**. البوت لو أضاف سؤال غلط اللاعب هيشوفه
في اللعبة على طول (انتاخبت النشر التلقائي)، فالفلتر لازم يكون متشدداً:
أي شك ⇒ رفض. وكمان بيوقف نفسه لو مش متأكد إن الفحص عدّى صح.

الاستخدام المحلي:
    python3 scripts/question_bot.py --dry-run --source curated
    python3 scripts/question_bot.py --limit 12 --source both
    python3 scripts/question_bot.py --self-test       # بدائل الاختبارات

المتغيرات البيئية (في CI بتيجي من GitHub Secrets، ومحلياً من ملف .env):
    SUPABASE_URL        مثال https://xyzcompany.supabase.co
    SUPABASE_KEY        service_role key (كتابة) — مفيش بديل آمن ليه

    مفتاح التوليد — أي مزوّد OpenAI-compatible، والاختيار أسهلهم OpenRouter:
    OPENROUTER_API_KEY  مفتاح الـAPI
    OPENROUTER_BASE_URL اختياري — الافتراضي https://openrouter.ai/api/v1
    OPENROUTER_MODEL    اختياري — الافتراضي google/gemini-2.5-flash-exp:free
    LLM_PROVIDER        auto | openrouter | gemini (auto = openrouter لو فيه
                        مفتاح، وإلا Gemini لو فيه GEMINI_API_KEY)
    GEMINI_API_KEY      بديل مباشر لمزود Google الأصلي

    محتوى الأسئلة:
    QUESTION_BOT_DAILY_CAP        سقف النشر اليومي (افتراضي 25)
    QUESTION_BOT_DIFFICULTY       mixed | easy | medium | hard | mostly_easy ...
    LLM_MAX_REQUESTS_PER_RUN      حارس حد طلبات OpenRouter المجاني (10)
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

# ---------------------------------------------------------------------------
# إعدادات ثابتة — لازم تفضل متزامنة مع قواعد التدقيق في التطبيق
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# قارئ .env — للتشغيل المحلي فقط
# ---------------------------------------------------------------------------

#: ملفات الـenv المرتبة بالأولوية. `baked=True` معناها إن الملف ده بيقرأه
#: كمان الـSecrets Gradle Plugin (`app/build.gradle.kts`) ويحط كل مفاتيحه في
#: `BuildConfig` — أي **أي مفتاح سرّي للبوت هنا بيترفع جوه الـAPK**.
#: عشان كده `scripts/.env` هو المكان الصحيح، والجذر للاعدادات العامة فقط.
_DOTENV_SOURCES = (
    ("scripts/.env", False),      # بجوار السكربت — البوت بس اللي بيقراه
    (".env", True),               # ملف التطبيق — بيتدهن في BuildConfig
    ("../.env", True),
)


def _dotenv_candidates() -> List[Tuple[str, bool]]:
    here = os.path.dirname(os.path.abspath(__file__))
    out: List[Tuple[str, bool]] = []
    for rel, baked in _DOTENV_SOURCES:
        path = rel if os.path.isabs(rel) else os.path.normpath(os.path.join(here, rel))
        out.append((path, baked))
    return out


def load_dotenv() -> Tuple[Optional[str], bool]:
    """
    يقرأ ملفات .env الموجودة (من غير ما يدهس env var حقيقي)، ويرجع
    (المسار الأساسي، هل الملف ده بيتدهن في الـAPK ولا لأ).

    `os.environ.setdefault` هو النقطة: المتغير اللي حدده الـrunner أو الـshell
    بيكسب دايماً، وده بيمنع ملف .env قديم يسرّب مفتاح غلط في CI.
    """
    primary: Optional[str] = None
    primary_baked = False
    for path, baked in _dotenv_candidates():
        if not os.path.isfile(path):
            continue
        try:
            with open(path, "r", encoding="utf-8") as handle:
                for line in handle:
                    line = line.strip()
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    key, _, value = line.partition("=")
                    key = key.strip()
                    value = value.strip().strip('"').strip("'")
                    if key and value:
                        os.environ.setdefault(key, value)
        except OSError:
            continue
        if primary is None:
            primary, primary_baked = path, baked
    return primary, primary_baked


LOADED_DOTENV, DOTENV_IS_BAKED_INTO_APK = load_dotenv()


def dotenv_warning() -> str:
    """
    تحذير صريح لو مفتاح التوليد جاي من ملف بيترفع جوه الـAPK.

    الفحص ده مش تجميل: `app/build.gradle.kts` بيقرأ `.env` ويحط **كل** مفاتيحه
    في `BuildConfig`، فمفتاح OpenRouter (أو الأسوأ: service_role) في ملف الجذر
    يبقى مشفّر بس جوه الـAPK، وأي حد يفتح الـ APK ياخد مفتاح يدفع بفلوسه.
    """
    if not DOTENV_IS_BAKED_INTO_APK or not (LLM_API_KEY or SUPABASE_KEY):
        return ""
    which = "OPENROUTER_API_KEY" if LLM_API_KEY else "SUPABASE_SERVICE_ROLE_KEY"
    return (
        f"   ⚠️  {which} مقروء من {LOADED_DOTENV} — وده ملف بيتدهن في BuildConfig "
        "جوه الـAPK. انقله لـ scripts/.env أو Secrets في CI."
    )


def _env(*names: str, default: str = "") -> str:
    """أول متغير مضبوط يكسب — عشان OpenRouter/Gemini يتبادلوا بلا التباس."""
    for name in names:
        value = os.environ.get(name, "").strip()
        if value:
            return value
    return default


# ---------------------------------------------------------------------------
# الإعدادات
# ---------------------------------------------------------------------------

SUPABASE_URL = _env("SUPABASE_URL").rstrip("/")
SUPABASE_KEY = _env("SUPABASE_SERVICE_ROLE_KEY", "SUPABASE_KEY")

GEMINI_API_KEY = _env("GEMINI_API_KEY")
GEMINI_MODEL = _env("GEMINI_MODEL", default="gemini-2.5-flash")
GEMINI_ENDPOINT = (
    "https://generativelanguage.googleapis.com/v1beta/models/"
    "{model}:generateContent"
)

# --- مزوّد LLM مفتوح (OpenRouter أو أي شيء يتكلم OpenAI-compatible) ---------
# التصميم: مسار واحد `POST {base}/chat/completions` بيغطي OpenRouter و
# Groq/Together/OpenAI/Neko/GitHub Models، عشان كده بنطلب JSON بالنص في
# الـprompt (مش بـ response_format) — Response format مش مضمون في كل مزوّد،
# والـparser بيتعامل مع الـfences والمقدمات النصية أصلاً.
OPENROUTER_BASE_URL = _env(
    "OPENROUTER_BASE_URL", "LLM_BASE_URL", default="https://openrouter.ai/api/v1"
)
LLM_PROVIDER = _env("LLM_PROVIDER", default="auto").strip().lower()
LLM_API_KEY = _env("OPENROUTER_API_KEY", "LLM_API_KEY", "OPENAI_API_KEY")
# نموذج افتراضي مجاني — لازم يفضل :free عشان الـkey المجاني يشتغل من غير رصيد
LLM_MODEL = _env("OPENROUTER_MODEL", "LLM_MODEL", default="google/gemini-2.5-flash-exp:free")
LLM_TEMPERATURE = float(_env("LLM_TEMPERATURE", default="0.85"))
LLM_MAX_TOKENS = int(_env("LLM_MAX_TOKENS", default="8000"))
#: ترويسة بيحبها OpenRouter للتحليل؛ مابتغيرش السلوك
LLM_APP_TITLE = _env("LLM_APP_TITLE", default="Malab El Tahaddi Question Bot")

#: أقصى عدد طلبات LLM في التشغيل الواحد. الحد ده **أهم من الـdaily cap**:
#: طبقة OpenRouter المجانية بترفض بعد 50 طلب/يوم (و20/دقيقة)، ولو سبناها
#: من غير governor البوت يخلص الرصيد على محاولة واحدة فاشلة ويوقف باقي اليوم.
LLM_MAX_REQUESTS_PER_RUN = int(_env("LLM_MAX_REQUESTS_PER_RUN", default="10"))

TABLE_QUESTIONS = "questions"
VIEW_FEED = "questions_feed"
HTTP_TIMEOUT_SECONDS = 40

#: أقصى عدد أسئلة ينشره البوت في اليوم — الحد اللي طلبته (نشر تلقائي + سقف).
#: الحماية دي لوحدها اللي بتمنع يوم يسبّب فيه Gemini هلوسة جماعية تفخّ الجدول.
DAILY_CAP = int(os.environ.get("QUESTION_BOT_DAILY_CAP", "25"))

#: أقصى عدد محاولات Gemini في التشغيل الواحد (حماية من حلقة فشل).
MAX_GENERATION_ATTEMPTS = 4

#: نسبة التشابه اللي بعدها بنعتبر السؤال مكرّر (0-1).
SIMILARITY_REJECT_THRESHOLD = 0.9

#: الأنواع الأربعة اللي التطبيق بيرسمها فعلاً. أي نوع تاني مرفوض لأن
#  مفيش شاشة بتقراه، فالسؤال هيبقى ميت في القاعدة.
VALID_GAME_TYPES = ("thirty_seconds", "sabaho", "true_false", "pvp")

VALID_DIFFICULTIES = ("easy", "medium", "hard")

# حدين واقعيين: قصير جداً = مفيهوش معلومة، طويل جداً = مابقراش في 30 ثانية.
MIN_QUESTION_LEN = 14
MAX_QUESTION_LEN = 180
MIN_OPTION_LEN = 1
MAX_OPTION_LEN = 60
MIN_OPTIONS = 2
MAX_OPTIONS = 5
MAX_CATEGORY_LEN = 40
MIN_EXPLANATION_LEN = 0  # مش إجباري، بس لو موجود لازم يبقى معقول
MAX_EXPLANATION_LEN = 260

# كلمات دلّت على أن النص placeholder أو تسريب من الـprompt ⇒ رفض فوري.
PLACEHOLDER_MARKERS = (
    "TODO", "TBD", "XXX", "???", "...", "lorem", "placeholder",
    "النص هنا", "ضع هنا", "مثال:", "FIXME", "None", "null",
    "[", "]", "{", "}", "<", ">",
)

# مواضيع خارج نطاق اللعبة/الحساسية: البوت بيرفضها حتى لو Gemini رجعها.
BANNED_KEYWORDS = (
    "انتحار", "مخدرات", "تفجير", "إرهاب", "سلاح", "عرق", "عنصري",
    "طائف", "دين", "شتم", "سب", "رشاوى", "تلاعب نتائج", "منشطات",
    "قمار", "رهان", "betting", "gambling",
)

# أسئلة "مغلقة زمنياً" (بتبطل مع الوقت) أو محتاجة مصدر رسمي دقيق.
# بنرفضها في وضع النشر التلقائي لأن ماحدش بيراجع قبل اللعب.
TIME_SENSITIVE_MARKERS = (
    "السعر الحالي", "أغلى لاعب الآن", "السوق الصيفي", "الميركاتو",
    "ترتيب الدوري حالياً", "الهداف الحالي", "أحدث انتقال",
)

# ---------------------------------------------------------------------------
# تطبيع النص — لازم يطابق normalizeForHash() في Kotlin حرفياً
# ---------------------------------------------------------------------------

# علامات التشكيل والتطويل العربية
_ARABIC_DIACRITICS = re.compile(r"[\u064B-\u0652\u0670\u0640]")
_WHITESPACE = re.compile(r"\s+")
# إزالة الترقيم الشائع في العربي والإنجليزي (بيغيّر البصمة من غير ما يغيّر المعنى)
_PUNCT = re.compile(r"[،؛:.！？!؟،ًْـ\"'“”‘’()ـ\-–—_/\\]+")
# توحيد الألف الهمزة والياء والتاء المربوطة — أخطاء إملائية شائعة من المولّد
_NORMALIZE_MAP = str.maketrans({
    "أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا",
    "ى": "ي", "ئ": "ي", "ؤ": "و",
    "ة": "ه",
    "ک": "ك", "ي": "ي",
    # الأرقام العربية-الهندية والفارسية → لاتينية: "٢٠٢٢" و "2022" نفس السؤال
    "٠": "0", "١": "1", "٢": "2", "٣": "3", "٤": "4",
    "٥": "5", "٦": "6", "٧": "7", "٨": "8", "٩": "9",
    "۰": "0", "۱": "1", "۲": "2", "۳": "3", "۴": "4",
    "۵": "5", "۶": "6", "۷": "7", "۸": "8", "۹": "9",
    "،": " ", "٫": ".", "٬": ",",
})


def normalize(text: str) -> str:
    """تطبيع نص عربي للمقارنة والبصمة: إزالة تشكيل/ترقيم، توحيد حروف، قصّ المسافات."""
    if not text:
        return ""
    stripped = unicodedata.normalize("NFC", text)
    stripped = _ARABIC_DIACRITICS.sub("", stripped)
    stripped = stripped.translate(_NORMALIZE_MAP)
    stripped = _PUNCT.sub(" ", stripped)
    stripped = stripped.casefold()
    return _WHITESPACE.sub(" ", stripped).strip()


def content_hash(game_type: str, question: str, answer: str) -> str:
    """sha256 على (النوع + السؤال + الإجابة الصحيحة) موحّدين.

    البصمة على النوع والإجابة مقصودة: نفس السؤال بصيغة صح/غلط يختلف عن
    نفس السؤال اختيار من متعدد، ونفس السؤال بإجابة مختلفة سؤال جديد فعلاً
    (تصحيح خطأ سابق) فيجب أن يُقبل.
    """
    payload = "|".join([
        game_type.strip().lower(),
        normalize(question),
        normalize(answer),
    ])
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def similarity(a: str, b: str) -> float:
    """نسبة تشابه نصي (difflib) على النص الموحّد — كشف المكرر بصياغة تانية."""
    if not a or not b:
        return 0.0
    if a == b:
        return 1.0
    # difflib.SequenceMatcher مع "junk" على المسافة = أسرع وأمتن للنصوص الطويلة
    import difflib
    return difflib.SequenceMatcher(None, a, b, autojunk=True).ratio()


# ---------------------------------------------------------------------------
# التدقيق — الـGatekeeping
# ---------------------------------------------------------------------------

class Rejected(Exception):
    """سؤال مرفوض — بيحمل سبب واحد يُسجّل في ملخّص التشغيل."""


def extract_answer_text(row: Dict[str, Any]) -> str:
    """النص الصحيح من السؤال — للاشم في البصمة وللتحقق من عدم التكرار."""
    options = row.get("options") or []
    if row.get("game_type") == "true_false":
        # 0 = «صح»، أي رقم تاني = «غلط» (مش truthiness عامة)
        try:
            return "صح" if int(row.get("correct_index", 0)) == 0 else "غلط"
        except (TypeError, ValueError):
            return "صح" if _as_bool(row.get("correct_index")) else "غلط"
    try:
        idx = int(row.get("correct_index", 0))
    except (TypeError, ValueError):
        return ""
    if 0 <= idx < len(options):
        return str(options[idx])
    return ""


def _as_bool(value: Any) -> bool:
    """تحويل متسامح لقيمة منطقية (SQLite بيزوّل boolean لـ 0/1)."""
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    if isinstance(value, str):
        return value.strip().lower() in ("1", "true", "yes", "صح")
    return False


def validate_question(row: Dict[str, Any]) -> Tuple[Dict[str, Any], str]:
    """يتحقق من سؤال ويعيد (النسخة المنظّفة، '') أو يرمي Rejected.

    القواعد هنا لازم تطابق validateQuestion() في Kotlin، لأن الاختلاف
    معناه سؤال مرّ من البوت ومابقاش صالح في اللعبة.
    """
    if not isinstance(row, dict):
        raise Rejected("ليس كائناً")

    game_type = str(row.get("game_type") or "").strip().lower()
    if game_type not in VALID_GAME_TYPES:
        raise Rejected(f"نوع غير مدعوم: {game_type!r}")

    question = normalize_text_field(row.get("question"))
    if len(question) < MIN_QUESTION_LEN:
        raise Rejected("السؤال قصير جداً")
    if len(question) > MAX_QUESTION_LEN:
        raise Rejected("السؤال طويل جداً على جولة 30 ثانية")

    if contains_placeholder(question):
        raise Rejected("يحتوي أثر placeholder")
    if contains_banned(question):
        raise Rejected("موضوع محظور")
    if game_type != "true_false" and contains_time_sensitive(question):
        raise Rejected("معلومة حساسة للزمن ومفيش مراجعة قبل النشر")

    difficulty = str(row.get("difficulty") or "medium").strip().lower()
    if difficulty not in VALID_DIFFICULTIES:
        difficulty = "medium"

    category = normalize_text_field(row.get("category"))[:MAX_CATEGORY_LEN]
    explanation = normalize_text_field(row.get("explanation"))
    if explanation and len(explanation) > MAX_EXPLANATION_LEN:
        explanation = explanation[:MAX_EXPLANATION_LEN].rsplit(" ", 1)[0]
    if explanation and len(explanation) < MIN_EXPLANATION_LEN:
        explanation = ""

    # فحص لغة النص: لازم يكون فيه قدر كافي من الحروف العربية
    if arabic_ratio(question) < 0.25:
        raise Rejected("النص مش عربي بشكل كافٍ")

    options, correct_index = _validate_options(row, game_type)

    answer = ""
    if options:
        answer = str(options[correct_index])
        if game_type != "true_false":
            # الإجابة ما تتكررش ك distractor (غلقة نموذجية في النماذج)
            for i, opt in enumerate(options):
                if i != correct_index and normalize(opt) == normalize(answer):
                    raise Rejected("الإجابة صحيحة ومكررّة كخيار خاطئ")

    cleaned: Dict[str, Any] = {
        "game_type": game_type,
        "question": question,
        "options": options,
        "correct_index": correct_index,
        "difficulty": difficulty,
        "category": category or "عامة",
        "explanation": explanation,
    }

    # الإجابة لازم تكون منطقية: مابتبقاش "None"/"لا شيء" لوحدها في نوع MCQ
    if game_type != "true_false" and normalize(answer) in ("", "none", "null", "لا شيء"):
        raise Rejected("الإجابة الصحيحة فارغة أو لا معنى لها")

    return cleaned, answer


def _validate_options(row: Dict[str, Any], game_type: str) -> Tuple[List[str], int]:
    """يفحص الخيارات ويصلّح ترتيبها."""
    # true_false أولاً: التطبيق بيرسم صح/غلط من نفسه، فمش منطق إننا نفرض
    # وجود options قبل ما نعرف النوع — البوت الصحيح مابيبعتش خيارات لهذا النوع
    if game_type == "true_false":
        # التطبيق بيرسم صح/غلط دائماً، فبنضيف الخيارات هنا مش بنتظرها من البوت.
        # دلالة الحقلين مختلفة ولازم محترمة: is_true = هل البيان صحيح،
        # بينما correct_index = رقم الخيار الصحيح (0 ⇒ «صح»، 1 ⇒ «غلط»).
        # خلط الاتنين كان بيقلب إجابة أي سؤال بعت correct_index=1.
        options = ["صح", "غلط"]
        flag = row.get("is_true")
        if flag is not None:
            return options, (0 if _as_bool(flag) else 1)

        raw_index = row.get("correct_index")
        if raw_index is None:
            raise Rejected("true_false يحتاج is_true أو correct_index")
        text = str(raw_index).strip().lower()
        # الأرقام بتعدّي زي ما هي (0 ⇒ «صح» = الخيار الأول، 1 ⇒ «غلط»)،
        # والكلمات بتتحوّل لمعناها: "صح"/true معناهم إن البيان صحيح ⇒ index 0
        if text in ("0", "صح", "صح.", "true", "yes"):
            return options, 0
        if text in ("1", "غلط", "غلط.", "false", "no"):
            return options, 1
        raise Rejected("true_false قيمة غير مفهومة")

    raw_options = row.get("options")
    if isinstance(raw_options, str):
        # النماذج بترجع أحياناً خيارات مفصولة بـ | أو , بدل JSON
        raw_options = [part for part in re.split(r"[|,،]", raw_options) if part.strip()]
    if not isinstance(raw_options, (list, tuple)):
        raise Rejected("options مش قائمة")

    options = [normalize_text_field(o) for o in raw_options]
    options = [o for o in options if len(o) >= MIN_OPTION_LEN]

    # كاش Room في التطبيق (offline_questions/cloud_questions) بيفصل الخيارات
    # بـ "|"، فأي خيار فيه الحرف ده هيتقسّم لخيارين هناك ويكسر السؤال.
    # الرفض هنا أرخص من فساد البيانات في كل الأجهزة.
    for opt in options:
        if "|" in opt:
            raise Rejected("خيار يحتوي | وهو فاصل محجوز في كاش التطبيق")
    if len(options) > MAX_OPTIONS:
        raise Rejected(f"عدد الخيارات أكتر من {MAX_OPTIONS}")
    if len(options) < MIN_OPTIONS:
        raise Rejected("الخيارات أقل من 2")

    # تكرار الخيارات = سؤال مكسور منطقياً
    seen: Dict[str, int] = {}
    for i, opt in enumerate(options):
        key = normalize(opt)
        if len(opt) > MAX_OPTION_LEN:
            raise Rejected("خيار طويل جداً")
        if key in seen:
            raise Rejected("خيارات مكررة")
        seen[key] = i

    raw_index = row.get("correct_index", None)
    if raw_index is None or (isinstance(raw_index, str) and not raw_index.strip()):
        # مفيش index خالص: لازم نجيب الإجابة من النص. الـfallback ده مش اختياري —
        # لو فترضنا 0 هنشر سؤال إجابته "أول خيار" بصمت، وده أسوأ من الرفض.
        answer_text = normalize_text_field(row.get("correct_answer"))
        correct = seen.get(normalize(answer_text), -1)
        if correct < 0:
            raise Rejected("مفيش correct_index ولا correct_answer صالح")
    else:
        try:
            correct = int(str(raw_index).strip())
        except (TypeError, ValueError):
            # أحياناً النماذج بترجع correct_answer كنص بدل index
            answer_text = normalize_text_field(row.get("correct_answer"))
            correct = seen.get(normalize(answer_text), -1)
            if correct < 0:
                raise Rejected("correct_index مش رقم ومفيش correct_answer صالح")

    if correct < 0 or correct >= len(options):
        raise Rejected("correct_index خارج الحدود")
    return options, correct


def normalize_text_field(value: Any) -> str:
    """تنظيف حقل نص: نص، سطر واحد، بدون تشكيل زائد أو مسافات مضاعفة."""
    if value is None:
        return ""
    text = value if isinstance(value, str) else str(value)
    text = text.replace("\r", " ").replace("\n", " ").replace("\t", " ")
    text = _ARABIC_DIACRITICS.sub("", text)
    return _WHITESPACE.sub(" ", text).strip()


def arabic_ratio(text: str) -> float:
    """نسبة الحروف العربية من إجمالي الحروف (مش المسافات والترقيم)."""
    letters = [c for c in text if c.isalpha()]
    if not letters:
        return 0.0
    arabic = [c for c in letters if "\u0600" <= c <= "\u06FF"]
    return len(arabic) / len(letters)


def contains_placeholder(text: str) -> bool:
    low = text.lower()
    return any(marker.lower() in low for marker in PLACEHOLDER_MARKERS)


_ARABIC_DEFINITE_PREFIX = "ال"


def banned_match(text: str) -> Optional[str]:
    """يرجع أول كلمة محظورة تطابق النص، أو None.

    مطابقة **الكلمة كاملة** مش substring: في العربي الـsubstring بيوهّم —
    «سب» (شتيمة) كانت جوه «كسب»، و«دين» جوه «العديد». بنقارن الكلمة كما هي
    وكمان بعد شيل «ال» التعريف، عشان «العنصري» تقع في الحظر زي «عنصري».
    العبارات متعددة الكلمات بتتقارن substring عادي لأن طولها بيكفي للتمييز.
    """
    lowered = normalize(text)
    if not lowered:
        return None
    tokens = [t for t in lowered.split(" ") if t]
    stems = set(tokens)
    for token in tokens:
        if token.startswith(_ARABIC_DEFINITE_PREFIX) and len(token) > len(_ARABIC_DEFINITE_PREFIX) + 1:
            stems.add(token[len(_ARABIC_DEFINITE_PREFIX):])

    for keyword in BANNED_KEYWORDS:
        normalized_key = normalize(keyword)
        if not normalized_key:
            continue
        if " " in normalized_key:
            if normalized_key in lowered:
                return keyword
        elif normalized_key in stems:
            return keyword
    return None


def contains_banned(text: str) -> bool:
    return banned_match(text) is not None


def contains_time_sensitive(text: str) -> bool:
    low = normalize(text)
    return any(normalize(k) in low for k in TIME_SENSITIVE_MARKERS)


# ---------------------------------------------------------------------------
# HTTP — urllib بس، من غير requests
# ---------------------------------------------------------------------------

def http_json(
    method: str,
    url: str,
    payload: Optional[Any] = None,
    headers: Optional[Dict[str, str]] = None,
    retries: int = 3,
) -> Any:
    """طلب JSON مع تراجع أُسّي. بيرمي RuntimeError برسالة فيها الجسم لو ممكن."""
    data = None
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")

    attempt = 0
    last_error = ""
    while attempt < max(1, retries):
        attempt += 1
        request = urllib.request.Request(url, data=data, method=method)
        request.add_header("Content-Type", "application/json")
        request.add_header("Accept", "application/json")
        for key, value in (headers or {}).items():
            request.add_header(key, value)
        try:
            with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
                body = response.read().decode("utf-8", "replace").strip()
                if not body:
                    return {}
                return json.loads(body)
        except urllib.error.HTTPError as error:  # فيه جسم خطأ مفيد
            body = ""
            try:
                body = error.read().decode("utf-8", "replace")[:800]
            except Exception:
                pass
            # 409 = conflict على اليونيكون ⇒ احنا عايزينه (تجاهل المكرر)
            if error.code == 409:
                return {"__conflict__": True, "body": body}
            last_error = f"HTTP {error.code}: {body or error.reason}"
            # 4xx ماعدا 408/429 = مفيش فايدة من إعادة المحاولة
            if 400 <= error.code < 500 and error.code not in (408, 429):
                raise RuntimeError(last_error)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
            last_error = f"{type(error).__name__}: {error}"
        time.sleep(min(2 ** attempt, 10))
    raise RuntimeError(last_error or "فشل الاتصال بدون تفاصيل")


def rest_url(path: str) -> str:
    if not SUPABASE_URL:
        raise RuntimeError("SUPABASE_URL مش مضبوط")
    return f"{SUPABASE_URL}/rest/v1/{path.lstrip('?')}"


def service_headers(extra: Optional[Dict[str, str]] = None) -> Dict[str, str]:
    if not SUPABASE_KEY:
        raise RuntimeError("مفتاح service_role مش مضبوط (SUPABASE_SERVICE_ROLE_KEY)")
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
    }
    headers.update(extra or {})
    return headers


# ---------------------------------------------------------------------------
# قراءة الحالة الحالية من القاعدة (لمنع التكرار)
# ---------------------------------------------------------------------------

def fetch_existing(game_type: str, limit: int = 4000) -> List[Dict[str, Any]]:
    """كل الأسئلة المعروفة (بما فيها pending/rejected) عشان ماحصلش تكرار."""
    params = (
        f"questions?select=question,options,correct_index,game_type,content_hash"
        f"&game_type=eq.{game_type}&order=id.asc&limit={limit}"
    )
    rows = http_json("GET", rest_url(params), headers=service_headers())
    return rows if isinstance(rows, list) else []


def count_published_last_24h(game_type: Optional[str] = None) -> int:
    """عدد المنشور في آخر 24 ساعة — ده اللي عليه الحد اليومي."""
    params = "questions?select=id&status=eq.published&published_at=gt.now()-24h"
    if game_type:
        params += f"&game_type=eq.{game_type}"
    # Prefer: count=exact + limit=1 → بنقرأ Count من الهيدر
    request_headers = service_headers({"Prefer": "count=exact"})
    url = rest_url(params + "&limit=1")
    data = None
    request = urllib.request.Request(url, data=data, method="GET")
    for key, value in request_headers.items():
        request.add_header(key, value)
    request.add_header("Range-Unit", "items")
    request.add_header("Range", "0-0")
    try:
        with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
            raw = response.headers.get("Content-Range", "")
            # مثال: items 0-0/7  ⇒ الرقم بعد / هو الإجمالي
            total = raw.rsplit("/", 1)[-1].strip()
            return int(total) if total.isdigit() else 0
    except Exception:
        return 0


# ---------------------------------------------------------------------------
# سياسة الصعوبة — «كام سهل / كام متوسط / كام صعب»
# ---------------------------------------------------------------------------
# مابقيناش نسيب النموذج يحدد لوحده لسببين: موديلات الطبقة المجانية بترجع
# غالباً «medium» في كل حاجة (أأمن اختيار عندها)، واللعبة نفسها محتاجة
# تدرّج عشان «30 ثانية» و«صباحو» ليهم صعوبة مختلفة في اللعب.
#
# الصيغ المقبولة في QUESTION_BOT_DIFFICULTY:
#   mixed          → توزيع متوازن easy/medium/hard (الافتراضي)
#   easy|medium|hard → كل الدفعة على مستوى واحد (بيُجبَر حرفياً)
#   mostly_easy    → 50% سهل / 35% متوسط / 15% صعب
#   mostly_hard    → 50% صعب / 35% متوسط / 15% سهل
#   medium_hard    → نصين، من غير أسئلة سهلة
#   easy=1,medium=2,hard=1 → أوزان صريحة

DIFFICULTY_POLICY_RAW = _env("QUESTION_BOT_DIFFICULTY", default="mixed").strip().lower()

_DIFFICULTY_PRESETS: Dict[str, Dict[str, int]] = {
    "mixed": {"easy": 1, "medium": 1, "hard": 1},
    "easy": {"easy": 1},
    "medium": {"medium": 1},
    "hard": {"hard": 1},
    "mostly_easy": {"easy": 10, "medium": 7, "hard": 3},
    "mostly_hard": {"hard": 10, "medium": 7, "easy": 3},
    "medium_hard": {"medium": 1, "hard": 1},
}


def parse_difficulty_policy(raw: str) -> Dict[str, int]:
    """`mixed`/preset/أوزان صريحة ← dict أوزان. أي حاجة مفهومة = mixed."""
    raw = (raw or "").strip().lower()
    if raw in _DIFFICULTY_PRESETS:
        return dict(_DIFFICULTY_PRESETS[raw])

    if "=" in raw:
        weights: Dict[str, int] = {}
        for part in raw.replace(";", ",").split(","):
            key, sep, value = part.partition("=")
            key = key.strip()
            if not sep or key not in VALID_DIFFICULTIES:
                continue
            try:
                weight = max(0, int(float(value.strip())))
            except ValueError:
                continue
            if weight:
                weights[key] = weight
        if weights:
            return weights

    return dict(_DIFFICULTY_PRESETS["mixed"])


#: سياسات preset أحادية القيمة = إجبار حرفي على كل سؤال في الدفعة.
#: «mixed» والأوزان الصريحة = تخطيط للـprompt بس، لأن النموذج من حقه يغلط
#: ونحنا بنفضّل سؤال بصعوبة مختلفة على إننا نرميه.
FORCED_DIFFICULTY = (
    next(iter(_DIFFICULTY_PRESETS[DIFFICULTY_POLICY_RAW]))
    if DIFFICULTY_POLICY_RAW in ("easy", "medium", "hard")
    else None
)

DIFFICULTY_WEIGHTS = parse_difficulty_policy(DIFFICULTY_POLICY_RAW)


def plan_difficulties(count: int) -> List[str]:
    """
    توزيع صعوبات لدفعة حجمها `count` — deterministic وبنفس الوزن المطلوب.

    بنوزّع بالباقي (مش بعشوائية) عشان الدفعة الصغيرة تفضل متوازنة: طلب 4
    أسئلة بأوزان 1/1/1 يرجع easy/medium/hard/easy، مش easy×4.
    """
    if count <= 0:
        return []
    names = list(DIFFICULTY_WEIGHTS.keys())
    weights = [DIFFICULTY_WEIGHTS[n] for n in names]
    total = sum(weights)

    plan: List[str] = []
    while len(plan) < count:
        for name, weight in zip(names, weights):
            # نصيب المستوى ده من الدفعة، مع ضمان إن كل مستوى يظهر مرة على الأقل
            share = max(1, round(count * weight / total)) if len(plan) < count else 0
            plan.extend([name] * share)
            if len(plan) >= count:
                break
    return plan[:count]


def difficulty_prompt_line(plan: Sequence[str]) -> str:
    """وصف human-readable للتوزيع المطلوب، بيحطه البوت في الـprompt."""
    if not plan:
        return ""
    counts: Dict[str, int] = {}
    for level in plan:
        counts[level] = counts.get(level, 0) + 1
    # بصيغة «سهل 2، متوسط 3» بدل «2 سهلة» — أقصر على النموذج وأسلم نحوياً،
    # وبنرجّع ترتيب VALID_DIFFICULTIES عشان الـprompt يفضل ثابتاً بين التشغيلات
    # (ده بيسهّل مقارنة اللوجات لو الموديل غيّر سلوكه).
    pretty = {"easy": "سهل", "medium": "متوسط", "hard": "صعب"}
    parts = [f"{pretty[lv]} {counts[lv]}" for lv in VALID_DIFFICULTIES if lv in counts]
    return (
        "التوزيع المطلوب للصعوبة: " + "، ".join(parts) + f" (من {len(plan)} سؤال). "
        "اكتب \"difficulty\" لكل سؤال بالإنجليزي: easy/medium/hard، وما تغيّرش المستوى."
    )


def enforce_difficulty(policy_plan: Sequence[str], index: int, difficulty: str) -> str:
    """
    المستوى النهائي للسؤال:
      - سياسة أحادية ⇒ نُجبرها (ضمان حقيقي مش رجاء).
      - غير كده ⇒ ناخد قيمة النموذج لو صالحة، وإلا المستوى المخطط له.
    """
    if FORCED_DIFFICULTY:
        return FORCED_DIFFICULTY
    if difficulty in VALID_DIFFICULTIES:
        return difficulty
    if index < len(policy_plan):
        return policy_plan[index]
    return "medium"


# ---------------------------------------------------------------------------
# حارس عدد الطلبات — طبقة مجانية = حد يومي على عدد الطلبات
# ---------------------------------------------------------------------------

class RequestBudget:
    """
    عداد طلبات LLM للتشغيل الواحد.

    ليه موجود؟ لأن الطبقة المجانية بحدّها بتفرق **عدد الطلبات** مش عدد
    الأسئلة (50 طلب/يوم و20/دقيقة على OpenRouter)، ولو الدفعة كلها اترفضت
    فيمكن نحرق 4 محاولات × 4 أنواع = 16 طلب في دقيقة. الحارس بيحوّل «تجاوز
    الحد» لحالة معروفة بنسجّلها ونوقف بيها بهدوء، بدل 429 متكرر يخلص
    رصيد اليوم قبل ما ينشر أي حاجة.
    """

    def __init__(self, limit: int) -> None:
        self.limit = max(0, limit)
        self.used = 0

    def acquire(self) -> bool:
        if self.used >= self.limit:
            return False
        self.used += 1
        return True

    @property
    def remaining(self) -> int:
        return max(0, self.limit - self.used)


LLM_BUDGET = RequestBudget(LLM_MAX_REQUESTS_PER_RUN)


# ---------------------------------------------------------------------------
# توليد بـ Gemini
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = """أنت مولّد أسئلة لعبة كروية عربية. مهمتك: أسئلة قصيرة، معلومتها صحيحة وموثوقة، وصياغة مصرية/فصحى مبسطة يفهمها شاب عربي.

ممنوع منعاً باتاً:
- أي معلومة تتغير مع الوقت (أسعار انتقال، ترتيب جدول حالي، الهداف الحالي، أخبار سوق).
- أي موضوع خارج الملعب (دين، سياسة، مخدرات، قمار، شتائم، اتهامات فساد).
- علامات ترقيم زائدة، أو أقواس مربعة، أو شرح داخل نص السؤال.
- تكرار نفس الفكرة بأكثر من صيغة في الدفعة.

مواضيع مسموحة وآمنة: تاريخ كأس العالم، بطولات الأندية، قوانين اللعبة، أرقام قياسية ثابتة، جغرافية الملاعب، المنتخبات، نجوم معروفون بإنجازاتهم الموثقة.

أرجع JSON فقط بالشكل:
{"questions": [
  {"game_type":"thirty_seconds|sabaho|true_false|pvp",
   "question":"نص السؤال",
   "options":["خيار1","خيار2","خيار3","خيار4"],
   "correct_index":0,
   "difficulty":"easy|medium|hard",
   "category":"مجال قصير",
   "explanation":"شرح من سطر واحد يذكر الحقيقة الصحيحة",
   "is_true":true}
]}

ملاحظات:
- في true_false: اكتب السؤال كجملة خبرية، وحط true في "is_true" لو الجملة صحيحة. تجاهل options.
- في MCQ: "correct_index" رقم starting من 0. لازم يكون الخيار الصحيح موجود فعلاً في القائمة.
- مابتحطش "correct_answer" نصي لو عارف تحط index.
"""


def build_prompt(game_type: str, count: int, existing_samples: Sequence[str]) -> str:
    samples = list(existing_samples)[:40]
    avoid = ""
    if samples:
        avoid = (
            "\n\nدي أسئلة موجودة بالفعل — ماتكررش أفكارها ولا صياغتها:\n"
            + "\n".join(f"- {s}" for s in samples)
        )
    difficulty_line = difficulty_prompt_line(plan_difficulties(count))
    return (
        f"أضف {count} سؤال جديد للنوع '{game_type}'. "
        f"{difficulty_line} "
        f"نوّع المجالات (تاريخ، أرقام قياسية، قوانين، جغرافيا ملاعب، منتخبات). "
        f"الرد JSON فقط بدون أي نص قبله أو بعده.{avoid}"
    )


def gemini_generate(game_type: str, count: int, existing_samples: Sequence[str]) -> List[Dict[str, Any]]:
    """استدعاء Gemini generateContent الأصلي (responseMimeType=json)."""
    if not GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY مش مضبوط — بنستخدم curated فقط")

    url = GEMINI_ENDPOINT.format(model=GEMINI_MODEL) + f"?key={GEMINI_API_KEY}"
    payload = {
        "systemInstruction": {"parts": [{"text": SYSTEM_PROMPT}]},
        "contents": [{"role": "user", "parts": [{"text": build_prompt(game_type, count, existing_samples)}]}],
        "generationConfig": {
            "temperature": 0.85,
            "maxOutputTokens": 8192,
            "responseMimeType": "application/json",
        },
    }

    body = http_json(
        "POST", url, payload=payload,
        headers={"x-goog-api-key": GEMINI_API_KEY}, retries=2,
    )

    text = _extract_gemini_text(body)
    if not text:
        raise RuntimeError("Gemini رجع رد فاضي")
    return parse_questions_json(text)


def resolve_provider() -> str:
    """
    أي مزوّد بنستخدم؟ `auto` = OpenRouter لو فيه مفتاح له، وإلا Gemini.

    المنطق ده مبيتعملش مرة واحدة عند الـimport عشان الاختبارات تقدر تغيّر
    الـenv وتعيد الحساب.
    """
    forced = LLM_PROVIDER
    if forced in ("openrouter", "openai", "generic"):
        return "openai"
    if forced == "gemini":
        return "gemini"
    if LLM_API_KEY:
        return "openai"
    if GEMINI_API_KEY:
        return "gemini"
    return "none"


def chat_completions_generate(
    game_type: str, count: int, existing_samples: Sequence[str]
) -> List[Dict[str, Any]]:
    """
    توليد عبر أي endpoint متوافق مع OpenAI (OpenRouter افتراضياً).

    ملاحظات على الاختيارات:
      - `response_format` متروك عمدًا: مش كل مزوّد مجاني بيدعمه، و400 بسببه
        يهدّي الدفعة كلها. `parse_questions_json` بيشيل ``` fences والنص
        الزايد قبل JSON، فطلب JSON بالنص كفاية وأأمن.
      - `max_tokens` كبير: 4-20 سؤال بعربي مشفّر بايتات كتير، والقطع في نص
        JSON = رد غير صالح بالكامل.
      - `stop` متروك: بعض المزوّدين بيحسبوه خطأ مع النصوص العربية.
    """
    if not LLM_API_KEY:
        raise RuntimeError("مفيش OPENROUTER_API_KEY — بنستخدم curated فقط")

    base = (OPENROUTER_BASE_URL or "https://openrouter.ai/api/v1").rstrip("/")
    url = base if base.endswith("/chat/completions") else base + "/chat/completions"

    payload = {
        "model": LLM_MODEL,
        "temperature": LLM_TEMPERATURE,
        "max_tokens": LLM_MAX_TOKENS,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": build_prompt(game_type, count, existing_samples)},
        ],
    }
    # OpenRouter بيطلب إعادة توجيه/عنوان للتصنيف؛ مش إجباري بس بيفيد في لوحة
    # التحكم، وبنحطه بس لما نكون على OpenRouter فعلاً.
    headers = {"Authorization": f"Bearer {LLM_API_KEY}"}
    if "openrouter.ai" in url:
        headers["HTTP-Referer"] = "https://github.com/hamama44/-"
        headers["X-Title"] = LLM_APP_TITLE

    body = http_json("POST", url, payload=payload, headers=headers, retries=3)

    text = _extract_chat_text(body)
    if not text:
        raise RuntimeError(f"المزوّد رجع رد فاضي (model={LLM_MODEL})")
    return parse_questions_json(text)


def _extract_chat_text(body: Any) -> str:
    """
    استخراج نص الرد من شكل OpenAI.

    بنمرّر على `choices[0].message.content` و`.text` و`[{"type":"text"}]`
    لأن المزوّدين المجانيين بيرجعوا أشكال مختلفة شوية، وده أرخص مكان نتحمّل
    فيه الاختلاف بدل ما نرمي رد صحيح كـ«فاضي».
    """
    if not isinstance(body, dict):
        return ""
    choices = body.get("choices")
    if not isinstance(choices, list) or not choices:
        return ""
    first = choices[0] if isinstance(choices[0], dict) else {}
    message = first.get("message") if isinstance(first, dict) else None

    candidates: List[Any] = []
    if isinstance(message, dict):
        candidates.append(message.get("content"))
        # بعض المزوّدين بيرجعوا قائمة أجزاء
        content = message.get("content")
        if isinstance(content, list):
            return "".join(
                str(part.get("text", ""))
                for part in content
                if isinstance(part, dict)
            )
    candidates.append(first.get("text"))

    for candidate in candidates:
        if isinstance(candidate, str) and candidate.strip():
            return candidate
    return ""


def generate_questions(
    game_type: str, count: int, existing_samples: Sequence[str]
) -> List[Dict[str, Any]]:
    """نقطة الدخول الوحيدة للتوليد — بتوزّع على المزوّد وبتحرس الميزانية."""
    provider = resolve_provider()
    if provider == "none":
        raise RuntimeError("مفيش مفتاح LLM (OpenRouter أو Gemini) — بنستخدم curated فقط")
    if not LLM_BUDGET.acquire():
        raise RuntimeError(
            f"ميزانية الطلبات خلصت ({LLM_MAX_REQUESTS_PER_RUN} طلب للتشغيل) — "
            "بنوقف بهدوء عشان ما نحرقش حد اليوم المجاني"
        )
    if provider == "gemini":
        return gemini_generate(game_type, count, existing_samples)
    return chat_completions_generate(game_type, count, existing_samples)


def _extract_gemini_text(body: Any) -> str:
    """استخراج text من شكل generateContent، مع تجاهل parts النصية الفارغة."""
    try:
        parts = body["candidates"][0]["content"]["parts"]
    except (KeyError, IndexError, TypeError):
        return ""
    chunks: List[str] = []
    for part in parts:
        if isinstance(part, dict) and isinstance(part.get("text"), str):
            chunks.append(part["text"])
    return "".join(chunks).strip()


def parse_questions_json(text: str) -> List[Dict[str, Any]]:
    """يقرأ JSON وقد يكون ملفوف في ``` fences أو فيه نص زايد قبله/بعده."""
    cleaned = text.strip()
    fence = re.match(r"^```(?:json)?\s*(.*?)\s*```$", cleaned, re.DOTALL)
    if fence:
        cleaned = fence.group(1).strip()

    parsed: Any = None
    with_context = cleaned
    if with_context and not with_context.startswith("{") and not with_context.startswith("["):
        # نشيل أي مقدمة نصية زي "Sure! Here is the JSON:"
        start = min(
            (i for i in (with_context.find("{"), with_context.find("[")) if i >= 0),
            default=-1,
        )
        if start > 0:
            with_context = with_context[start:]
    try:
        parsed = json.loads(with_context)
    except json.JSONDecodeError:
        # آخر محاولة: أول كائن JSON كامل
        decoder = json.JSONDecoder()
        start = with_context.find("{")
        if start < 0:
            raise Rejected("مش JSON")
        try:
            parsed, _ = decoder.raw_decode(with_context[start:])
        except json.JSONDecodeError:
            raise Rejected("JSON غير صالح")

    if isinstance(parsed, dict):
        rows = parsed.get("questions") or parsed.get("items") or parsed.get("data")
    else:
        rows = parsed

    if not isinstance(rows, list):
        raise Rejected("مفيش قائمة questions في الرد")

    # flatten: لو النموذج رجّع قائمة فيها قوائم متداخلة (شائع)
    flat: List[Dict[str, Any]] = []
    for row in rows:
        if isinstance(row, dict):
            flat.append(row)
        elif isinstance(row, list):
            flat.extend(r for r in row if isinstance(r, dict))
    return flat


# ---------------------------------------------------------------------------
# نشر
# ---------------------------------------------------------------------------

def publish(rows: Sequence[Dict[str, Any]], dry_run: bool) -> Dict[str, int]:
    """إدراج بالـchunking مع تجاهل المكرر على level القاعدة.

    بنستخدم content-hash uniq index مع Prefer: resolution=ignore-duplicates
    عشان حتى لو تشغيلين تزامنين، المكرر يتجاهل في القاعدة مش في البوت بس.
    """
    stats = {"inserted": 0, "skipped": len(rows), "failed": 0}
    if not rows:
        stats["skipped"] = 0
        return stats

    payload = []
    for row in rows:
        payload.append({
            "id": row["id"],
            "game_type": row["game_type"],
            "question": row["question"],
            "options": row["options"],
            "correct_index": row["correct_index"],
            "difficulty": row["difficulty"],
            "category": row["category"],
            "explanation": row["explanation"],
            "content_hash": row["content_hash"],
            "source": row["source"],
            "status": "published",  # النشر التلقائي زي ما اخترت
        })

    if dry_run:
        print(json.dumps({"dry_run_would_insert": payload}, ensure_ascii=False, indent=2))
        stats["inserted"] = len(payload)
        stats["skipped"] = 0
        return stats

    chunk_size = 25
    for i in range(0, len(payload), chunk_size):
        chunk = payload[i:i + chunk_size]
        try:
            result = http_json(
                "POST",
                rest_url(TABLE_QUESTIONS),
                payload=chunk,
                headers=service_headers({
                    "Prefer": "return=representation,resolution=ignore-duplicates",
                }),
            )
            if isinstance(result, list):
                stats["inserted"] += len(result)
                stats["skipped"] += len(chunk) - len(result)
            elif isinstance(result, dict) and result.get("__conflict__"):
                stats["skipped"] += len(chunk)
            else:
                stats["inserted"] += len(chunk)
        except RuntimeError as error:
            print(f"  ! فشل نشر دفعة: {error}", file=sys.stderr)
            stats["failed"] += len(chunk)
    return stats


def make_id(game_type: str, hash_value: str, source: str) -> str:
    """id ثابت من البصمة: إعادة تشغيل البوت على نفس السؤال = نفس الـid."""
    prefix = {"bot": "bot", "curated": "cur", "seed": "sd", "admin": "adm"}.get(source, "bot")
    return f"q_{prefix}_{game_type[:3]}_{hash_value[:16]}"


# ---------------------------------------------------------------------------
# المصادر
# ---------------------------------------------------------------------------

def load_curated(path: str) -> List[Dict[str, Any]]:
    """قراءة ملف curated.json (قائمة أو {questions:[...]})."""
    if not os.path.exists(path):
        return []
    with open(path, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    if isinstance(data, dict):
        data = data.get("questions") or []
    if not isinstance(data, list):
        raise RuntimeError(f"ملف {path} لازم يكون قائمة أسئلة")
    out = []
    for row in data:
        if isinstance(row, dict):
            row = dict(row)
            row.setdefault("source", "curated")
            out.append(row)
    return out


def dedupe_against_existing(
    candidates: List[Dict[str, Any]],
    existing: Sequence[Dict[str, Any]],
) -> Tuple[List[Dict[str, Any]], List[str]]:
    """يشيل المرشّحات المكررة مقارنة بالموجود في القاعدة + داخل نفس الدفعة.

    الطبقتين مهمين: البصمة ترفض النسخة الحرفية، ونسبة التشابه ترفض
    «مين كسب المونديال 2022؟» بعد ما «مين فاز بكأس العالم 2022؟» موجودة.
    """
    existing_hashes = {r.get("content_hash") for r in existing if r.get("content_hash")}
    existing_norms: List[Tuple[str, str]] = []
    for row in existing:
        text = normalize(str(row.get("question") or ""))
        if text:
            existing_norms.append((text, str(row.get("game_type") or "")))

    accepted: List[Dict[str, Any]] = []
    notes: List[str] = []
    batch_hashes: set = set()
    batch_norms: List[str] = []

    for row in candidates:
        question_norm = normalize(row["question"])
        game_type = row["game_type"]
        hash_value = content_hash(game_type, row["question"], row.get("__answer", ""))

        if hash_value in existing_hashes or hash_value in batch_hashes:
            notes.append(f"تجاهل (بصمة مكررة): {row['question'][:60]}")
            continue

        similar = [
            text for text, gt in existing_norms
            if gt == game_type and similarity(text, question_norm) >= SIMILARITY_REJECT_THRESHOLD
        ]
        if similar:
            notes.append(f"تجاهل (تشابه {similarity(similar[0], question_norm):.2f}): {row['question'][:60]}")
            continue

        if any(similarity(q, question_norm) >= SIMILARITY_REJECT_THRESHOLD for q in batch_norms):
            notes.append(f"تجاهل (مكرر داخل الدفعة): {row['question'][:60]}")
            continue

        row["content_hash"] = hash_value
        accepted.append(row)
        batch_hashes.add(hash_value)
        batch_norms.append(question_norm)
    return accepted, notes


# ---------------------------------------------------------------------------
# التجميع
# ---------------------------------------------------------------------------

def collect_for_game_type(
    game_type: str,
    wanted: int,
    use_gemini: bool,
    curated: Sequence[Dict[str, Any]],
    existing: Sequence[Dict[str, Any]],
    log: List[str],
) -> Tuple[List[Dict[str, Any]], int]:
    """`use_gemini` معناها «استخدم الـLLM» — المزوّد نفسه بيحدده `resolve_provider`."""
    """يولّد (أو ياخد من curated) لحد ما يلمّى `wanted` سؤال صالح، ويرفض الباقي.

    لو Gemini رجع أسئلة كلها مرفوضة، بنستنى تاني مرة — بحد أقصى
    MAX_GENERATION_ATTEMPTS — عشان الدفعات المرفوضة متتسببش في صمت كامل.
    """
    accepted: List[Dict[str, Any]] = []
    rejected_count = 0

    # 1) curated أولاً: مضمون ومراجع يدوياً، وبيتخطى التوليد في الحالة دي
    for row in curated:
        if str(row.get("game_type") or "").strip().lower() != game_type:
            continue
        if len(accepted) >= wanted:
            break
        try:
            cleaned, answer = validate_question(row)
        except Rejected as reason:
            rejected_count += 1
            log.append(f"  رفض curated: {reason} — {str(row.get('question'))[:60]}")
            continue
        cleaned["__answer"] = answer
        cleaned["source"] = "curated"
        if FORCED_DIFFICULTY:
            cleaned["difficulty"] = FORCED_DIFFICULTY
        accepted.append(cleaned)

    if len(accepted) >= wanted or not use_gemini:
        return accepted, rejected_count

    # 2) توليد الـLLM للباقي
    remaining = wanted - len(accepted)
    samples = [str(r.get("question") or "") for r in existing if r.get("question")]
    for attempt in range(MAX_GENERATION_ATTEMPTS):
        if len(accepted) >= wanted:
            break
        batch_size = min(remaining + 4, 20)  # نفخّ شوية عشان الرفض الطبيعي
        plan = plan_difficulties(batch_size)
        try:
            rows = generate_questions(game_type, batch_size, samples)
        except (RuntimeError, Rejected) as error:
            log.append(f"  توليد {game_type} فشل (محاولة {attempt + 1}): {error}")
            # تجاوز حد الطلبات مش حالة نعيد فيها المحاولة: بنقفل الدفعة كلها
            # عشان ما نكملش حرق الطلبات بعد أول 429.
            if "ميزانية الطلبات" in str(error):
                log.append(f"  ⏹️ إيقاف توليد {game_type}: الميزانية خلصت")
                break
            time.sleep(1.5)
            continue

        for row in rows:
            if len(accepted) >= wanted:
                break
            row = dict(row)
            row.setdefault("game_type", game_type)
            try:
                cleaned, answer = validate_question(row)
            except Rejected as reason:
                rejected_count += 1
                continue
            cleaned["__answer"] = answer
            cleaned["source"] = "bot"
            cleaned["difficulty"] = enforce_difficulty(
                plan, len(accepted) % max(1, len(plan)), str(cleaned.get("difficulty") or "")
            )
            accepted.append(cleaned)

        remaining = wanted - len(accepted)
        if remaining > 0:
            # نعيد الطلب لو الدفعة كلها اترفضت — عشان الدفعة الفارضة متتسببش
            # في تشغيل فاضي كامل
            time.sleep(1.0)
    return accepted, rejected_count


def run(
    limit: int,
    dry_run: bool,
    use_gemini: bool,
    curated_path: str,
    game_types: Sequence[str],
) -> int:
    """التشغيل الكامل. بيرجع 0 نجاح و 1 فشل جزئي و 2 فشل كامل."""
    started = time.time()
    log: List[str] = []
    print(f"▶️  سؤال بوت V3 — الحد اليومي {DAILY_CAP} | dry_run={dry_run}")
    warning = dotenv_warning()
    if warning:
        print(warning)

    curated = load_curated(curated_path)
    if curated:
        print(f"   ملف curated: {len(curated)} سؤال من {curated_path}")
    else:
        print("   مفيش ملف curated — هيعتمد على التوليد لو متاح")

    # الميزانية بتتبداً من الصفر كل تشغيل: الـglobal بيتشارك فيه كل الأنواع
    # الأربعة في نفس الـprocess، ودي الطريقة الوحيدة نضمن بيها إن إجمالي
    # الطلبات تحت حد المزوّد مش لكل نوع على حدة.
    LLM_BUDGET.used = 0

    provider = resolve_provider()
    if use_gemini and provider == "none":
        print(
            "   ⚠️  مفيش OPENROUTER_API_KEY ولا GEMINI_API_KEY — "
            "هيتخطى التوليد ويعتمد على curated بس"
        )
        use_gemini = False
    elif use_gemini:
        where = f"{LLM_MODEL} @ {OPENROUTER_BASE_URL}" if provider == "openai" else f"{GEMINI_MODEL} @ google"
        print(f"   🧠 مزوّد التوليد: {provider} — {where}")
        print(
            f"   📊 سياسة الصعوبة: {DIFFICULTY_POLICY_RAW} → "
            f"{DIFFICULTY_WEIGHTS} (إجبار: {FORCED_DIFFICULTY or 'لأ'})"
        )
        print(f"   🎛️  ميزانية الطلبات في التشغيل ده: {LLM_BUDGET.limit}")

    total_accepted: List[Dict[str, Any]] = []
    total_rejected = 0

    # الحد اليومي على مستوى التشغيل كله، مش لكل نوع: ده اللي بيحمي الجدول
    already_today = 0
    if not dry_run:
        try:
            already_today = sum(count_published_last_24h(gt) for gt in game_types)
        except Exception as error:
            # لو الفشل في العدّ نقف بأمان: أأمن من تجاوز الحد اليومي
            print(f"   ❌ فشل عدّ المنشور اليوم: {error}", file=sys.stderr)
            return 2
        if already_today >= DAILY_CAP:
            print(f"   ⛔ وصلنا الحد اليومي ({already_today}/{DAILY_CAP}) — مفيش نشر النهاردة")
            return 0

        budget = DAILY_CAP - already_today
    else:
        budget = limit * len(game_types)

    per_type_budget = max(1, budget // len(game_types)) if budget else 0
    if per_type_budget <= 0:
        print("   ⛔ الميزانية صفر بعد الحد اليومي")
        return 0

    for game_type in game_types:
        try:
            existing = fetch_existing(game_type)
        except Exception as error:
            # في dry-run مفيش كتابة أصلاً، فمافيش داعي نفشل لو مفيش مفاتيح:
            # بنكمل بمنطق «مفيش مكرر معروف» وبنطبع التحذير. ده بيخلّي
            # python3 scripts/question_bot.py --dry-run يشتغل محلياً من غير إعداد.
            if dry_run:
                print(f"   ⚠️  تجاهل فحص التكرار لـ{game_type} ({error}) — dry-run")
                existing = []
            else:
                print(f"   ❌ فشل قراءة الأسئلة الموجودة لـ{game_type}: {error}", file=sys.stderr)
                return 2

        print(f"   {game_type}: {len(existing)} سؤال موجود في القاعدة")
        wanted = min(per_type_budget, limit)
        accepted, rejected = collect_for_game_type(
            game_type=game_type,
            wanted=wanted,
            use_gemini=use_gemini,
            curated=curated,
            existing=existing,
            log=log,
        )
        total_rejected += rejected

        deduped, notes = dedupe_against_existing(accepted, existing)
        for note in notes:
            log.append(f"  {note}")

        for row in deduped:
            row["id"] = make_id(row["game_type"], row["content_hash"], row["source"])
        total_accepted.extend(deduped)
        print(
            f"      → {len(deduped)} صالح للنشر، {len(accepted) - len(deduped)} مكرر، "
            f"{rejected} مرفوض"
        )

    if total_accepted:
        stats = publish(total_accepted, dry_run=dry_run)
    else:
        stats = {"inserted": 0, "skipped": 0, "failed": 0}

    print("\n" + "=" * 68)
    print(f"✅ أُنشر/يُنشر: {stats['inserted']} | تجاهل مكرر: {stats['skipped']} | "
          f"رفض التدقيق: {total_rejected} | فشل: {stats['failed']}")
    if log:
        print("-" * 68)
        for line in log[:40]:
            print(line)
        if len(log) > 40:
            print(f"   … و{len(log) - 40} سطر تاني")
    print(f"⏱️  زمن التشغيل: {time.time() - started:.1f} ثانية")
    print("=" * 68)

    # ملخّص الـworkflow لو شغال في GitHub Actions
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        try:
            with open(summary, "a", encoding="utf-8") as handle:
                handle.write("## 🤖 Question Bot\n\n")
                handle.write(f"| النتيجة | العدد |\n|---|---|\n")
                handle.write(f"| نُشر | {stats['inserted']} |\n")
                handle.write(f"| مُكرر (تجاهل) | {stats['skipped']} |\n")
                handle.write(f"| رُفض (تدقيق) | {total_rejected} |\n")
                handle.write(f"| فشل | {stats['failed']} |\n")
                handle.write(f"\nالحد اليومي: {already_today}→"
                             f"{already_today + stats['inserted']}/{DAILY_CAP}\n")
        except OSError:
            pass

    if stats["failed"]:
        return 1
    return 0


# ---------------------------------------------------------------------------
# اختبارات مدمجة — بتشتغل على أي Python بدون pytest
# ---------------------------------------------------------------------------

def self_test() -> int:
    """اختبارات وحدة مصغّرة لمنطق التدقيق والبصمة (مفيش شبكة خالص)."""
    failures: List[str] = []

    def check(name: str, condition: bool) -> None:
        if not condition:
            failures.append(name)

    def expect_reject(name: str, row: Dict[str, Any]) -> None:
        try:
            validate_question(row)
            failures.append(f"{name}: كان لازم يترفض")
        except Rejected:
            pass

    # normalize: تشكيل، همزات، ترقيم، مسافات
    check("normalize يقص التشكيل ويوحّد الة", normalize("كُرة القَدَم") == "كره القدم")
    check("normalize يوحّد الهمزات", normalize("آرجنتين") == "ارجنتين")
    check("normalize يوحّد الأرقام العربية", normalize("2022") == normalize("٢٠٢٢"))
    check("normalize يتجاهل الترقيم", normalize("مين كسب؟") == normalize("مين كسب"))
    check("normalize يعيد '' للفارغ", normalize("   ") == "")
    check("normalize يوحّد الـة/ه", normalize("كرة") == normalize("كره"))

    # hash: نفس السؤال ⇒ نفس البصمة، إجابة مختلفة ⇒ بصمة مختلفة
    h1 = content_hash("thirty_seconds", "مين كسب كأس العالم ٢٠٢٢ في قطر؟", "الأرجنتين")
    h2 = content_hash("thirty_seconds", "مين فاز بكأس العالم 2022؟", "الأرجنتين")
    h3 = content_hash("thirty_seconds", "مين فاز بكأس العالم 2022؟", "فرنسا")
    check("hash يتجاهل الترقيم والتشكيل",
          content_hash("thirty_seconds", "مَيْن كَسَبَ كَأْسُ العَالَم ٢٠٢٢ فِي قَطَر", "الأرجنتين") == h1)
    check("hash حساس للإجابة", h1 != h3)
    check("hash نوع السؤال مؤثر", content_hash("pvp", "س", "ج") != content_hash("sabaho", "س", "ج"))

    # أسئلة صالحة
    good = {
        "game_type": "thirty_seconds",
        "question": "مين كسب كأس العالم ٢٠٢٢ في قطر؟",
        "options": ["الأرجنتين", "فرنسا", "البرازيل", "كرواتيا"],
        "correct_index": 0,
        "difficulty": "easy",
        "category": "كأس العالم",
        "explanation": "الأرجنتين فازت بركلات الترجيح.",
    }
    cleaned, answer = validate_question(good)
    check("سؤال صالح يعدّى", cleaned["game_type"] == "thirty_seconds" and answer == "الأرجنتين")
    check("category محفوظ", cleaned["category"] == "كأس العالم")

    tf = {"game_type": "true_false", "question": "كأس العالم بيتلعب كل أربع سنين.", "is_true": True}
    cleaned_tf, answer_tf = validate_question(tf)
    check("true_false يولّد خياراته", cleaned_tf["options"] == ["صح", "غلط"])
    check("true_false is_true=True ⇒ index 0", cleaned_tf["correct_index"] == 0)
    cleaned_tf2, _ = validate_question({**tf, "is_true": False})
    check("true_false is_true=False ⇒ index 1", cleaned_tf2["correct_index"] == 1)
    # bool لازم يتعامل معاه صح مش int('true')
    check("true_false يقبل 'صح' كنص", validate_question({**tf, "is_true": "صح"})[0]["correct_index"] == 0)
    tf_no_flag = {k: v for k, v in tf.items() if k != "is_true"}
    check("true_false يقبل correct_index نصي", validate_question({**tf_no_flag, "correct_index": "1"})[0]["correct_index"] == 1)

    # رفض: index خارج الحدود
    expect_reject("correct_index خارج الحدود", {**good, "correct_index": 9})
    expect_reject("correct_index سالب", {**good, "correct_index": -1})
    # رفض: خيار مكرر
    expect_reject("خيار مكرر", {**good, "options": ["الأرجنتين", "الأرجنتين", "فرنسا"]})
    # رفض: الإجابة موجودة ك distractor
    expect_reject("الإجابة مكررة كخيار خاطئ",
                  {**good, "options": ["الأرجنتين", "فرنسا", "الأرجنتين"], "correct_index": 0})
    # رفض: سؤال قصير
    expect_reject("سؤال قصير", {**good, "question": "مين؟"})
    # رفض: placeholder / أقواس
    expect_reject("placeholder", {**good, "question": "مين كسب [النص هنا]؟"})
    # رفض: موضوع محظور
    expect_reject("موضوع محظور", {**good, "question": "مين استخدم منشطات في كأس العالم؟"})
    # رفض: معلومة حساسة للزمن في MCQ
    expect_reject("حساس للزمن", {**good, "question": "مين أغلى لاعب في السوق الصيفي؟"})
    # رفض: مش عربي
    expect_reject("نص إنجليزي صِرف", {**good, "question": "Who won the World Cup in 2022?"})
    # رفض: عدد خيارات كبير
    expect_reject("خيارات كتير", {**good, "options": ["ا", "ب", "ت", "ث", "ج", "ح"]})
    # رفض: خيارات أقل من اتنين
    expect_reject("خيار واحد", {**good, "options": ["الأرجنتين"]})
    # رفض: نوع غير مدعوم
    expect_reject("نوع غلط", {**good, "game_type": "cricket"})
    # قبول: خيارات نصية مفصولة بفواصل (شكل شائع من النماذج)
    check("options مفصولة بفواصل تتحل",
          validate_question({**good, "options": "الأرجنتين,فرنسا,كرواتيا"})[0]["options"]
          == ["الأرجنتين", "فرنسا", "كرواتيا"])
    check("options مفصولة بـ| تتحل",
          validate_question({**good, "options": "الأرجنتين|فرنسا"})[0]["options"]
          == ["الأرجنتين", "فرنسا"])
    expect_reject("options مش قائمة أصلاً", {**good, "options": 42})
    expect_reject("خيار فيه الفاصل المحجوز |", {**good, "options": ["الأرجنتين|فرنسا", "كرواتيا", "بلجيكا"]})
    expect_reject("مفيش index ولا correct_answer صالح",
                  {k: v for k, v in good.items() if k != "correct_index"} | {"correct_answer": "أياكس"})
    # الـfallback الخطر: بدون index وبدون answer لازم يترفض مش ياخد 0
    expect_reject("مفيش index خالص", {k: v for k, v in good.items() if k != "correct_index"})
    # difficulty غلط ⇒ medium مش رفض
    check("difficulty غلط يتصحح", validate_question({**good, "difficulty": "خرافية"})[0]["difficulty"] == "medium")

    # استخراج الإجابة من نص بدل index (سلوك النماذج الشائع)
    from_text = {
        "game_type": "pvp",
        "question": "أنهي نادي أكثر من كسب دوري أبطال أوروبا؟",
        "options": ["ريال مدريد", "ميلان", "ليفربول"],
        "correct_answer": "ريال مدريد",
    }
    check("correct_answer نصي يتحوّل لـ index", validate_question(from_text)[0]["correct_index"] == 0)
    expect_reject("correct_answer مش في الخيارات",
                  {**from_text, "correct_answer": "أياكس"})


    # dedup
    existing_rows = [{"question": "مين كسب كأس العالم ٢٠٢٢ في قطر",
                     "game_type": "thirty_seconds", "content_hash": ""}]
    cand = {
        "game_type": "thirty_seconds",
        "question": "مين كسب كأس العالم ٢٠٢٢ في قطر فعلاً",  # زيادة كلمة واحدة
        "options": ["الأرجنتين", "فرنسا"],
        "correct_index": 0,
        "__answer": "الأرجنتين",
    }
    accepted, notes = dedupe_against_existing([cand], existing_rows)
    check("dedup بيشيل المكرر شبه الحرفي", accepted == [] and len(notes) == 1)

    # dedup داخل نفس الدفعة
    twin = dict(cand)
    twin["question"] = "مين كسب كأس العالم 2022 في قطر؟"
    accepted2, notes2 = dedupe_against_existing([twin, dict(twin)], [])
    check("dedup داخل الدفعة", len(accepted2) == 1 and len(notes2) == 1)

    # JSON parsing مع fences ونص تمهيدي
    wrapped = "Sure!\n```json\n{\"questions\": [{\"a\": 1}]}\n```"
    check("يفك ```json fence", parse_questions_json(wrapped) == [{"a": 1}])
    check("يتحمل نص قبل JSON", parse_questions_json("Result: {\"questions\":[{\"b\":2}]}")[0] == {"b": 2})
    check("يتحمل قائمة مباشرة", parse_questions_json('[{"c": 3}]') == [{"c": 3}])
    try:
        parse_questions_json("لا يوجد JSON هنا")
        failures.append("كان لازم يرفض نص مش JSON")
    except Rejected:
        pass
    check("يفكّ المصفوفة المتداخلة", len(parse_questions_json('{"questions":[[{"x":1},{"y":2}]]}')) == 2)

    # id ثابت من البصمة
    id1 = make_id("thirty_seconds", h1, "bot")
    id2 = make_id("thirty_seconds", h1, "bot")
    check("make_id ثابت (idempotent)", id1 == id2)
    check("make_id فيه النوع", "thi" in id1)
    check("make_id بيراعي المصدر", make_id("pvp", h1, "curated").startswith("q_cur_"))
    check("طول الـid معقول", len(id1) <= 40)

    # arabic_ratio
    check("arabic_ratio 1 للنص العربي", abs(arabic_ratio("كرة القدم") - 1.0) < 1e-9)
    check("arabic_ratio 0 للإنجليزي", arabic_ratio("football only") == 0.0)
    check("arabic_ratio 0 للفارغ", arabic_ratio("") == 0.0)

    # similarity
    check("similarity 1 للمتشابه", similarity("كرةالقدم", "كرةالقدم") == 1.0)
    check("similarity 0 للفارغ", similarity("", "x") == 0.0)

    if failures:
        print(f"❌ {len(failures)} اختبار فشل:")
        for item in failures:
            print(f"   - {item}")
        return 1
    # --- سياسة الصعوبة (QUESTION_BOT_DIFFICULTY) ---
    check("preset mixed = 3 مستويات", parse_difficulty_policy("mixed") == {"easy": 1, "medium": 1, "hard": 1})
    check("preset غير معروف → mixed", parse_difficulty_policy("خرافية") == {"easy": 1, "medium": 1, "hard": 1})
    check("أوزان صريحة", parse_difficulty_policy("easy=1,medium=2,hard=1") == {"easy": 1, "medium": 2, "hard": 1})
    check("أوزان تتجاهل مفتاح غلط",
          parse_difficulty_policy("easy=1,nope=9") == {"easy": 1})
    check("صعوبة أحادية بتصفّي الأوزان", parse_difficulty_policy("hard") == {"hard": 1})

    plan = plan_difficulties(6)
    check("التوزيع بيغطي العدد المطلوب", len(plan) == 6)
    check("التوزيع المتوازن بيشمل التلات مستويات", set(plan) == {"easy", "medium", "hard"})
    check("count=0 → فاضي", plan_difficulties(0) == [])

    with_counts = plan_difficulties(4)
    check(
        "دفعة صغيرة مابتسبورش مستوى whole",
        len(with_counts) == 4 and len(set(with_counts)) >= 2,
    )

    # enforce: السياسة الأحادية بتجبّر حتى لو النموذج خالف
    saved_forced = globals()["FORCED_DIFFICULTY"]
    globals()["FORCED_DIFFICULTY"] = "hard"
    check("إجبار أحادية", enforce_difficulty(["easy"], 0, "easy") == "hard")
    globals()["FORCED_DIFFICULTY"] = None
    check("في mixed نسيب قيمة النموذج الصالحة",
          enforce_difficulty(["easy"], 0, "medium") == "medium")
    check("قيمة غير صالحة → المستوى المخطط له",
          enforce_difficulty(["hard"], 0, "لاشيء") == "hard")
    globals()["FORCED_DIFFICULTY"] = saved_forced

    line = difficulty_prompt_line(["easy", "easy", "hard"])
    check("سطر الـprompt بيشمل العدد العربي والإنجليزي",
          "سهل 2" in line and "صعب 1" in line and "difficulty" in line)
    check("الترتيب ثابت (easy قبل medium قبل hard)",
          line.index("سهل") < line.index("متوسط") if "متوسط" in line else True)

    # --- حارس الميزانية ---
    budget = RequestBudget(2)
    check("أول طلبين وعد", budget.acquire() and budget.acquire())
    check("التالت مرفوض", not budget.acquire())
    check("remaining = 0", budget.remaining == 0)
    budget.used = 0
    check("إعادة الضبط بترجّع الحق", budget.acquire())
    check("limit سالب → مفيش طلبات خالص", not RequestBudget(0).acquire())

    # --- استخراج رد OpenAI-compatible ---
    check(
        "message.content نص",
        _extract_chat_text({"choices": [{"message": {"content": "{\"questions\":[]}"}}]})
        == '{"questions":[]}',
    )
    check(
        "message.content قائمة أجزاء (شائع في مزوّدين مجانيين)",
        _extract_chat_text({"choices": [{"message": {"content": [{"type": "text", "text": "مرحبا"}]}}]})
        == "مرحبا",
    )
    check("first.text كبديل", _extract_chat_text({"choices": [{"text": "hi"}]}) == "hi")
    check("رد مهشّم → فاضي مش انفجار", _extract_chat_text({"choices": []}) == "")
    check("body مش dict → فاضي", _extract_chat_text("oops") == "")

    # --- resolve_provider / .env ---
    saved_provider = globals()["LLM_PROVIDER"]
    globals()["LLM_PROVIDER"] = "openrouter"
    check("provider صريح openrouter → openai", resolve_provider() == "openai")
    globals()["LLM_PROVIDER"] = "gemini"
    check("provider صريح gemini", resolve_provider() == "gemini")
    globals()["LLM_PROVIDER"] = "auto"
    check(
        "auto بيفضّل المفتاح العام لو موجود",
        resolve_provider() == ("openai" if LLM_API_KEY else ("gemini" if GEMINI_API_KEY else "none"))
    )
    globals()["LLM_PROVIDER"] = saved_provider

    cands = _dotenv_candidates()
    check("مسارات .env نسبية (مافيهاش مسار مطلق يابس)",
          all(not os.path.isabs(rel) for rel, _ in _DOTENV_SOURCES) and len(cands) == len(_DOTENV_SOURCES))
    check("scripts/.env مش متعلّم كملف بيتدهن في الـAPK",
          _DOTENV_SOURCES[0][1] is False and _DOTENV_SOURCES[1][1] is True)

    print("✅ كل الاختبارات المدمجة عدّت")
    return 0


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="بوت إضافة أسئلة لملعب التحدي")
    parser.add_argument("--limit", type=int, default=6,
                        help="أقصى عدد أسئلة لكل نوع في التشغيل (افتراضي 6)")
    parser.add_argument("--dry-run", action="store_true",
                        help="يطبع اللي هينشر من غير ما يكتب في القاعدة")
    parser.add_argument("--source", choices=("both", "gemini", "llm", "openrouter", "curated"),
                        default="both", help="llm/openrouter = نفس المعنى بتاع gemini (حسب --provider)")
    parser.add_argument("--provider", choices=("auto", "openrouter", "gemini"), default=None,
                        help="المزوّد؛ auto = OpenRouter لو فيه مفتاح له وإلا Gemini")
    parser.add_argument("--model", default=None, help="اسم الموديل (بيدهس OPENROUTER_MODEL/GEMINI_MODEL)")
    parser.add_argument("--base-url", default=None, help="base URL للـchat completions")
    parser.add_argument("--api-key", default=None, help="مفتاح الـAPI للمزوّد المفتوح")
    parser.add_argument("--difficulty", default=None,
                        help="mixed | easy | medium | hard | mostly_easy | mostly_hard | medium_hard | easy=1,medium=2,hard=1")
    parser.add_argument("--curated", default=os.path.join(os.path.dirname(__file__), "questions_curated.json"))
    parser.add_argument("--game-types", default=",".join(VALID_GAME_TYPES))
    parser.add_argument("--self-test", action="store_true", help="يشغّل الاختبارات المدمجة ويقف")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()

    game_types = tuple(g.strip() for g in args.game_types.split(",") if g.strip() in VALID_GAME_TYPES)
    if not game_types:
        print("❌ مفيش نوع سؤال صالح في --game-types", file=sys.stderr)
        return 2

    global LLM_PROVIDER, LLM_MODEL, OPENROUTER_BASE_URL, LLM_API_KEY, \
        DIFFICULTY_POLICY_RAW, DIFFICULTY_WEIGHTS, FORCED_DIFFICULTY

    if args.provider:
        LLM_PROVIDER = args.provider
    if args.model:
        LLM_MODEL = args.model
        # لو الموديل صريح ومفيش مفتاح عام، نجرب ناخد مفتاح Gemini لو هو المطلوب
    if args.base_url:
        OPENROUTER_BASE_URL = args.base_url
    if args.api_key:
        LLM_API_KEY = args.api_key
    if args.difficulty:
        DIFFICULTY_POLICY_RAW = args.difficulty.strip().lower()
        DIFFICULTY_WEIGHTS = parse_difficulty_policy(DIFFICULTY_POLICY_RAW)
        FORCED_DIFFICULTY = (
            DIFFICULTY_POLICY_RAW if DIFFICULTY_POLICY_RAW in VALID_DIFFICULTIES else None
        )

    use_gemini = args.source in ("both", "gemini", "llm", "openrouter")
    try:
        return run(
            limit=max(1, args.limit),
            dry_run=args.dry_run,
            use_gemini=use_gemini,
            curated_path=args.curated,
            game_types=game_types,
        )
    except RuntimeError as error:
        print(f"❌ فشل: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
