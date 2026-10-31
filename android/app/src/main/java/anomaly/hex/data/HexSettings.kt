package anomaly.hex.data

import android.content.Context
import android.content.SharedPreferences
import anomaly.hex.logic.DictationLanguages
import anomaly.hex.logic.DictationStyle
import anomaly.hex.logic.EnginePreference
import anomaly.hex.logic.WhisperModelSpec
import java.util.Locale

class HexSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hex", Context.MODE_PRIVATE)

    fun onboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING, false)

    fun setOnboardingComplete() {
        prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
    }

    fun language() = DictationLanguages.byTag(
        prefs.getString(KEY_LANGUAGE, null)
            ?: DictationLanguages.defaultTag(Locale.getDefault().toLanguageTag()),
    )

    fun setLanguage(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE, DictationLanguages.byTag(tag).tag).apply()
    }

    fun style(): DictationStyle = DictationStyle.byId(prefs.getString(KEY_STYLE, null))

    fun setStyle(style: DictationStyle) {
        prefs.edit().putString(KEY_STYLE, style.name).apply()
    }

    fun preference(): EnginePreference = EnginePreference.byId(prefs.getString(KEY_ENGINE, null))

    fun setPreference(preference: EnginePreference) {
        prefs.edit().putString(KEY_ENGINE, preference.name).apply()
    }

    fun model(): WhisperModelSpec = WhisperModelSpec.byId(prefs.getString(KEY_MODEL, null))

    fun setModel(model: WhisperModelSpec) {
        prefs.edit().putString(KEY_MODEL, model.id).apply()
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val KEY_ONBOARDING = "onboarding_complete"
        const val KEY_LANGUAGE = "language_tag"
        const val KEY_STYLE = "style"
        const val KEY_ENGINE = "engine"
        const val KEY_MODEL = "whisper_model"
    }
}
