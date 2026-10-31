package anomaly.hex.logic

import java.util.Locale

enum class DictationStyle(val label: String, val hint: String) {
    POLISHED("Polished", "Clean punctuation and filler trim"),
    CASUAL("Casual", "Lowercase texting, no trailing period"),
    NOTES("Notes", "Spoken bullets and line breaks"),
    VERBATIM("Verbatim", "Exact words from the model"),
    ;

    companion object {
        fun byId(id: String?): DictationStyle =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: POLISHED
    }
}

/**
 * Same post-processing as the iPhone keyboard: fillers, sentence case,
 * casual lowercase, and spoken note markers.
 */
object TranscriptFormatter {
    fun format(raw: String, style: DictationStyle): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return when (style) {
            DictationStyle.VERBATIM -> collapseHorizontalSpaces(trimmed)
            DictationStyle.POLISHED -> polishSentences(stripStandaloneFillers(trimmed))
            DictationStyle.CASUAL -> casual(stripStandaloneFillers(trimmed))
            DictationStyle.NOTES -> formatNotes(stripStandaloneFillers(trimmed))
        }
    }

    fun insertionText(formatted: String, beforeContext: String?, afterContext: String?): String {
        if (formatted.isEmpty()) return ""
        var output = formatted
        if (!beforeContext.isNullOrEmpty()) {
            val last = beforeContext.last()
            val needsLeading = !last.isWhitespace() &&
                last != '(' && last != '[' && last != '{' &&
                last != '"' && last != '“' &&
                !output.startsWith("\n") &&
                !isClosingPunctuation(output.firstOrNull())
            if (needsLeading) output = " $output"
        }
        if (!afterContext.isNullOrEmpty()) {
            val first = afterContext.first()
            val needsTrailing = !first.isWhitespace() &&
                !isClosingPunctuation(first) &&
                !output.endsWith("\n") &&
                !output.endsWith(" ")
            if (needsTrailing) output += " "
        }
        return output
    }

    private fun casual(cleaned: String): String {
        var lowered = fixSpacingBeforePunctuation(collapseHorizontalSpaces(cleaned).lowercase(Locale.ROOT))
        if (lowered.endsWith(".") && lowered.dropLast(1).none { it == '.' || it == '!' || it == '?' }) {
            lowered = lowered.dropLast(1)
        }
        return lowered.trim()
    }

    private fun stripStandaloneFillers(text: String): String {
        val stripped = fillerPattern.replace(text, " ")
        return collapseHorizontalSpaces(stripped)
    }

    private fun polishSentences(text: String): String {
        if (text.isEmpty()) return ""
        var normalized = fixSpacingBeforePunctuation(collapseHorizontalSpaces(text))
        normalized = capitalizeSentenceStarts(normalized)
        val last = normalized.lastOrNull()
        if (last != null && !".!?…\"”)]".contains(last)) normalized += "."
        return normalized
    }

    private fun formatNotes(text: String): String {
        if (text.isEmpty()) return ""
        var working = text
        for ((pattern, template) in noteReplacements) {
            working = pattern.replace(working, template)
        }
        return working.split('\n').joinToString("\n") { line ->
            val trimmedLine = collapseHorizontalSpaces(line)
            if (trimmedLine.startsWith("- ")) {
                val body = trimmedLine.drop(2).trim()
                if (body.isEmpty()) "" else "- " + capitalizeFirstLetter(fixSpacingBeforePunctuation(body))
            } else {
                capitalizeFirstLetter(fixSpacingBeforePunctuation(trimmedLine))
            }
        }.trim()
    }

    private fun collapseHorizontalSpaces(text: String): String =
        text.split('\n').joinToString("\n") { line ->
            horizontalSpace.replace(line, " ").trim()
        }.trim()

    private fun fixSpacingBeforePunctuation(text: String): String {
        val tightened = spaceBeforePunctuation.replace(text, "$1")
        return leadingPunctuation.replace(tightened, "").trim()
    }

    private fun capitalizeSentenceStarts(text: String): String {
        val chars = text.toCharArray()
        var capitalizeNext = true
        for (index in chars.indices) {
            val ch = chars[index]
            if (capitalizeNext && ch.isLetter()) {
                chars[index] = ch.toString().uppercase(Locale.ROOT).first()
                capitalizeNext = false
            } else if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                capitalizeNext = true
            } else if (!ch.isWhitespace() && ch != '"' && ch != '“' && ch != '(') {
                capitalizeNext = false
            }
        }
        return String(chars)
    }

    private fun capitalizeFirstLetter(text: String): String {
        val index = text.indexOfFirst { it.isLetter() }
        if (index < 0) return text
        val upper = text[index].toString().uppercase(Locale.ROOT)
        return text.replaceRange(index, index + 1, upper)
    }

    private fun isClosingPunctuation(character: Char?): Boolean {
        if (character == null) return false
        return ".,!?;:)]}”".contains(character)
    }

    private val fillerPattern =
        Regex("""(?i)(?:^|(?<=[\s,]))(?:um+|uh+|er+|ah+)(?=(?:[\s,.!?]|$)),?\s*""")
    private val horizontalSpace = Regex("""[ \t]+""")
    private val spaceBeforePunctuation = Regex("""\s+([,.;:!?])""")
    private val leadingPunctuation = Regex("""^[,;:]+\s*""")
    private val noteReplacements = listOf(
        Regex("""(?i)\bnew paragraph\b[,.]?\s*""") to "\n\n",
        Regex("""(?i)\bnew line\b[,.]?\s*""") to "\n",
        Regex("""(?i)(?:^|\s+)(?:bullet point|next bullet|bullet)\b[:,-]?\s*""") to "\n- ",
    )
}
