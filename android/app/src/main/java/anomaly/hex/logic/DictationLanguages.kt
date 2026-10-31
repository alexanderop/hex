package anomaly.hex.logic

data class DictationLanguage(
    val tag: String,
    val whisperCode: String,
    val label: String,
)

enum class WhisperModelSpec(
    val id: String,
    val fileName: String,
    val url: String,
    val label: String,
) {
    TINY(
        "tiny",
        "ggml-tiny.bin",
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
        "Tiny",
    ),
    BASE(
        "base",
        "ggml-base.bin",
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
        "Base",
    ),
    ;

    companion object {
        const val MIN_BYTES = 20L * 1024L * 1024L

        fun byId(id: String?): WhisperModelSpec =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: TINY
    }
}

object DictationLanguages {
    val all: List<DictationLanguage> = listOf(
        DictationLanguage("en-US", "en", "English (US)"),
        DictationLanguage("en-GB", "en", "English (UK)"),
        DictationLanguage("de-DE", "de", "German"),
        DictationLanguage("es-ES", "es", "Spanish"),
        DictationLanguage("fr-FR", "fr", "French"),
        DictationLanguage("it-IT", "it", "Italian"),
        DictationLanguage("pt-BR", "pt", "Portuguese (Brazil)"),
        DictationLanguage("nl-NL", "nl", "Dutch"),
        DictationLanguage("pl-PL", "pl", "Polish"),
        DictationLanguage("ru-RU", "ru", "Russian"),
        DictationLanguage("uk-UA", "uk", "Ukrainian"),
        DictationLanguage("sv-SE", "sv", "Swedish"),
        DictationLanguage("da-DK", "da", "Danish"),
        DictationLanguage("nb-NO", "no", "Norwegian"),
        DictationLanguage("fi-FI", "fi", "Finnish"),
        DictationLanguage("tr-TR", "tr", "Turkish"),
        DictationLanguage("ar-SA", "ar", "Arabic"),
        DictationLanguage("hi-IN", "hi", "Hindi"),
        DictationLanguage("ja-JP", "ja", "Japanese"),
        DictationLanguage("ko-KR", "ko", "Korean"),
        DictationLanguage("zh-CN", "zh", "Chinese"),
    )

    fun byTag(tag: String?): DictationLanguage {
        if (tag.isNullOrBlank()) return all.first()
        all.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.let { return it }
        val language = tag.substringBefore('-').substringBefore('_')
        return all.firstOrNull { it.whisperCode.equals(language, ignoreCase = true) } ?: all.first()
    }

    fun defaultTag(deviceTag: String): String = byTag(deviceTag).tag
}

enum class DictationPhase {
    Idle,
    Listening,
    Transcribing,
    Failed,
}
