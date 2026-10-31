package anomaly.hex.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptFormatterTest {
    @Test
    fun polishedTrimsFillersAndClosesTheSentence() {
        assertEquals("", TranscriptFormatter.format("   ", DictationStyle.POLISHED))
        assertEquals("Hello world.", TranscriptFormatter.format("  hello   world ", DictationStyle.POLISHED))
        assertEquals("Hello there.", TranscriptFormatter.format("um hello uh there", DictationStyle.POLISHED))
        assertEquals("Hello.", TranscriptFormatter.format("um, hello", DictationStyle.POLISHED))
        assertEquals("Already done.", TranscriptFormatter.format("already done.", DictationStyle.POLISHED))
        assertEquals("Is it ready?", TranscriptFormatter.format("is it ready?", DictationStyle.POLISHED))
    }

    @Test
    fun casualDropsASingleTrailingPeriod() {
        assertEquals("hello world", TranscriptFormatter.format("Um, Hello World.", DictationStyle.CASUAL))
        assertEquals("wait. really.", TranscriptFormatter.format("Wait. Really.", DictationStyle.CASUAL))
        assertEquals("hello!", TranscriptFormatter.format("Hello!", DictationStyle.CASUAL))
    }

    @Test
    fun verbatimCollapsesSpacesOnly() {
        assertEquals("um hi", TranscriptFormatter.format("  um   hi ", DictationStyle.VERBATIM))
        assertEquals("A B\nC", TranscriptFormatter.format("  A   B \n  C  ", DictationStyle.VERBATIM))
    }

    @Test
    fun notesTurnsSpokenMarkersIntoLines() {
        assertEquals(
            "Buy milk\n- Eggs\nBread",
            TranscriptFormatter.format("buy milk bullet eggs new line bread", DictationStyle.NOTES),
        )
    }

    @Test
    fun insertionSpacesAroundTheCursor() {
        assertEquals(
            " there",
            TranscriptFormatter.insertionText("there", "Hello", null),
        )
        assertEquals(
            "there",
            TranscriptFormatter.insertionText("there", "Hello ", null),
        )
        assertEquals(
            ".com",
            TranscriptFormatter.insertionText(".com", "hex", null),
        )
        assertEquals(
            "there ",
            TranscriptFormatter.insertionText("there", null, "world"),
        )
        assertEquals("", TranscriptFormatter.insertionText("", "Hello", "world"))
    }
}
