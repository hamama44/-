package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testArabicNormalization() {
        fun normalizeArabic(text: String): String {
            return text.trim()
                .lowercase()
                .replace(Regex("[\\p{Mn}]"), "")
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ة', 'ه')
                .replace('ى', 'ي')
                .replace("عبد ", "عبد")
                .replace(" ", "")
        }

        assertEquals("محمدصلاح", normalizeArabic("مُحَمَّد صَلَاح"))
        assertEquals("اشرفحكيمي", normalizeArabic("أشرف حكيمي"))
        assertEquals("ياسينبونو", normalizeArabic("ياسين بونو "))
    }
}
