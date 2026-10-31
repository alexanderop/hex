package anomaly.hex.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class TextInsertionTest {
    @Test
    fun replacesTheSelectionAndSpacesAroundIt() {
        val inserted = TextInsertion.apply("abcdef", 2, 4, "x", DictationStyle.POLISHED)
        assertEquals("ab X. ef", inserted.text)
        assertEquals(6, inserted.cursor)
    }

    @Test
    fun blankDictationLeavesTheFieldAlone() {
        val inserted = TextInsertion.apply("hello", 5, 5, "   ", DictationStyle.POLISHED)
        assertEquals("hello", inserted.text)
        assertEquals(5, inserted.cursor)
    }

    @Test
    fun deviceLanguageFallsBackToTheCatalog() {
        assertEquals("de-DE", DictationLanguages.defaultTag("de-AT"))
        assertEquals("en-US", DictationLanguages.defaultTag("en-US"))
        assertEquals("en-US", DictationLanguages.defaultTag("zz"))
        assertEquals("no", DictationLanguages.byTag("nb-NO").whisperCode)
    }
}
