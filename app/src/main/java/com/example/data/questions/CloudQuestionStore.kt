package com.example.data.questions

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.data.PvPQuestion
import com.example.data.SabahoQuestion
import com.example.data.ThirtySecondsQuestion
import com.example.data.TrueFalseQuestion
import com.example.data.supabase.SupabaseQuestion
import com.example.data.supabase.SupabaseQuestionsDao
import kotlinx.coroutines.flow.Flow

/**
 * كاش الأسئلة السحابية في Room.
 *
 * ليه كاش جديد مش إعادة استخدام `offline_questions`؟ لأن الجدول ده بيُملأ
 * مرة واحدة من الأسئلة المضمّنة في APK (`seedOfflineQuestionsIfEmpty`)،
 * ولو خلطنا المحتوى السحابي فيه مابقدرناش نميّز «جديد من السيرفر» عن
 * «منسخة من الـAPK» ولا ننضّف القديم من غير ما نلمس المحتوى المضمّن.
 */
@Entity(
    tableName = "cloud_questions",
    indices = [
        Index(value = ["gameType"]),
        Index(value = ["contentHash"], unique = true)
    ]
)
data class CloudQuestionEntity(
    /** نفس الـid في Supabase ⇒ الكتابة المكررة بتعمل replace مش تكرار صفوف. */
    @PrimaryKey
    val id: String,
    val gameType: String,
    val question: String,
    /**
     * الخيارات مفصولة بـ `|` — نفس اصطلاح `offline_questions` القائم.
     * البوت بيفرض إن مفيش خيار فيه الحرف ده (انظر `question_bot.py`) فالتقسيم
     * آمن؛ و`toDomain` بيفلتر الفراغات تاني كحزام أمان.
     */
    val optionsString: String,
    val correctIndex: Int,
    val difficulty: String,
    val category: String,
    val explanation: String,
    /** `bot` | `curated` | `seed` | `admin` */
    val source: String,
    /** بصمة المحتوى — بتُستخدم لإزالة التكرار مع الأسئلة المضمّنة في APK. */
    val contentHash: String,
    val publishedAtMillis: Long,
    val cachedAtMillis: Long
) {
    val optionsList: List<String>
        get() = optionsString.split('|').map { it.trim() }.filter { it.isNotBlank() }

    companion object {
        const val SEPARATOR = "|"

        /** تحويل صف سحابي → كيان كاش. `null` لو الصف مش صالح للعب. */
        fun from(
            row: SupabaseQuestion,
            nowMillis: Long,
            publishedAtMillis: Long
        ): CloudQuestionEntity? {
            if (!SupabaseQuestionsDao.isPlayable(row, row.gameType)) return null
            val options = row.options.map { it.trim() }.filter { it.isNotBlank() }
                // أسئلة صح/غلط بتيجي بدون خيارات من البوت (الجدول مابيعرضش
                // أي حاجة للنوع ده)، فبنولّدها هنا. من غير السطر ده كل
                // أسئلة true_false كانت اترفضت من الكاش بصمت.
                .ifEmpty {
                    if (row.gameType == SupabaseQuestionsDao.GAME_TRUE_FALSE) {
                        listOf("صح", "غلط")
                    } else {
                        emptyList()
                    }
                }
            if (options.isEmpty()) return null

            val index = if (row.gameType == SupabaseQuestionsDao.GAME_TRUE_FALSE) {
                if (row.correctIndex == 0) 0 else 1
            } else {
                row.correctIndex.coerceIn(0, options.size - 1)
            }

            return CloudQuestionEntity(
                id = row.id,
                gameType = row.gameType,
                question = row.question.trim(),
                optionsString = options.joinToString(SEPARATOR),
                correctIndex = index,
                difficulty = row.difficulty?.takeIf { it.isNotBlank() } ?: "medium",
                category = row.category?.takeIf { it.isNotBlank() } ?: "عامة",
                explanation = row.explanation?.trim().orEmpty(),
                source = row.source ?: "bot",
                contentHash = SupabaseQuestionsDao.contentHash(
                    row.gameType,
                    row.question,
                    SupabaseQuestionsDao.answerText(row)
                ),
                publishedAtMillis = publishedAtMillis,
                cachedAtMillis = nowMillis
            )
        }
    }
}

/** الوصول لجدول كاش الأسئلة السحابية. */
@Dao
interface CloudQuestionDao {

    @Query("SELECT * FROM cloud_questions WHERE gameType = :gameType ORDER BY publishedAtMillis DESC")
    fun observeByGameType(gameType: String): Flow<List<CloudQuestionEntity>>

    @Query("SELECT * FROM cloud_questions WHERE gameType = :gameType ORDER BY publishedAtMillis DESC LIMIT :limit")
    suspend fun getByGameType(gameType: String, limit: Int): List<CloudQuestionEntity>

    @Query("SELECT COUNT(*) FROM cloud_questions")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cloud_questions")
    suspend fun getTotalCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<CloudQuestionEntity>)

    /**
     * حذف الصفوف اللي مش في القائمة المعطاة لنوع معين — نضافة بعد المزامنة:
     * سؤال اترفض أو اتسحب من السيرفر ما يفضلش معروض للاعب للأبد.
     */
    @Query(
        "DELETE FROM cloud_questions WHERE gameType = :gameType " +
            "AND id NOT IN (:keepIds)"
    )
    suspend fun pruneGameTypeNotIn(gameType: String, keepIds: List<String>): Int

    /**
     * يقصّ أقدم الأسئلة في النوع الواحد عشان الكاش مايكبرش للأبد.
     *
     * بيشيل من `publishedAtMillis` الأقدم لحد ما يفضل [keep] — يعني المحتوى
     * الجديد دايماً محفوظ، والقديم بيتنضّف. ده آمن لأن الأصل في Supabase
     * (مفيش فقدان بيانات) ولأن الأسئلة المضمّنة في APK قائمة لوحدها.
     *
     * `LIMIT` جوه الـsubquery قانوني في SQLite، و`id` مفتوح أساسي فمش
     * محتاجين ROWID.
     */
    @Query(
        "DELETE FROM cloud_questions WHERE gameType = :gameType AND id NOT IN (" +
            "SELECT id FROM cloud_questions WHERE gameType = :gameType " +
            "ORDER BY publishedAtMillis DESC LIMIT :keep)"
    )
    suspend fun pruneGameTypeOverflow(gameType: String, keep: Int): Int

    @Query("DELETE FROM cloud_questions")
    suspend fun clearAll()
}

// ---------------------------------------------------------------------------
// كيان → نماذج اللعبة
// ---------------------------------------------------------------------------

fun CloudQuestionEntity.toThirtySeconds(): ThirtySecondsQuestion = ThirtySecondsQuestion(
    id = id,
    question = question,
    options = optionsList,
    correctIndex = correctIndex.coerceIn(0, (optionsList.size - 1).coerceAtLeast(0)),
    category = category.ifBlank { "عامة" }
)

fun CloudQuestionEntity.toSabaho(): SabahoQuestion = SabahoQuestion(
    id = id,
    difficulty = SupabaseQuestionsDao.parseDifficulty(difficulty),
    question = question,
    options = optionsList,
    correctIndex = correctIndex.coerceIn(0, (optionsList.size - 1).coerceAtLeast(0)),
    explanation = explanation
)

/** 0 ⇒ «صح»، غير كده ⇒ «غلط» — نفس دلالة البوت والـDAO. */
fun CloudQuestionEntity.toTrueFalse(): TrueFalseQuestion = TrueFalseQuestion(
    id = id,
    statement = question,
    isTrue = correctIndex == 0,
    explanation = explanation,
    category = category.ifBlank { "عامة" }
)

fun CloudQuestionEntity.toPvp(): PvPQuestion = PvPQuestion(
    id = id,
    question = question,
    options = optionsList,
    correctIndex = correctIndex.coerceIn(0, (optionsList.size - 1).coerceAtLeast(0))
)
