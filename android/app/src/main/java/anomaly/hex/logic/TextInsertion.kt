package anomaly.hex.logic

data class Insertion(val text: String, val cursor: Int)

object TextInsertion {
    fun apply(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        dictated: String,
        style: DictationStyle,
    ): Insertion {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(start, text.length)
        val formatted = TranscriptFormatter.format(dictated, style)
        if (formatted.isEmpty()) return Insertion(text, start)
        val merged = TranscriptFormatter.insertionText(
            formatted,
            text.substring(0, start),
            text.substring(end),
        )
        val updated = text.substring(0, start) + merged + text.substring(end)
        return Insertion(updated, start + merged.length)
    }
}
