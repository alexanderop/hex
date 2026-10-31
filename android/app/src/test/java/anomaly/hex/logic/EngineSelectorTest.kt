package anomaly.hex.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineSelectorTest {
    @Test
    fun autoPrefersOnDeviceThenStandardThenWhisper() {
        assertEquals(EngineChoice.ON_DEVICE, EngineSelector.select(snap(onDevice = true, system = true)))
        assertEquals(EngineChoice.SYSTEM, EngineSelector.select(snap(onDevice = false, system = true)))
        assertEquals(
            EngineChoice.WHISPER,
            EngineSelector.select(snap(onDevice = false, system = false, whisper = true)),
        )
        assertEquals(EngineChoice.NONE, EngineSelector.select(snap()))
    }

    @Test
    fun manualSwitchOverridesTheChain() {
        assertEquals(
            EngineChoice.WHISPER,
            EngineSelector.select(snap(preference = EnginePreference.WHISPER, onDevice = true, whisper = true)),
        )
        assertEquals(
            EngineChoice.ON_DEVICE,
            EngineSelector.select(snap(preference = EnginePreference.WHISPER, onDevice = true, whisper = false)),
        )
        assertEquals(
            EngineChoice.WHISPER,
            EngineSelector.select(snap(preference = EnginePreference.SYSTEM, whisper = true)),
        )
        assertEquals(
            EngineChoice.ON_DEVICE,
            EngineSelector.select(snap(preference = EnginePreference.SYSTEM, onDevice = true, whisper = true)),
        )
    }

    @Test
    fun autoSkipsASystemThatAlreadyFailed() {
        assertEquals(
            EngineChoice.WHISPER,
            EngineSelector.select(snap(onDevice = true, system = true, whisper = true, systemFailed = true)),
        )
        assertEquals(
            EngineChoice.ON_DEVICE,
            EngineSelector.select(snap(onDevice = true, systemFailed = true, whisper = false)),
        )
    }

    @Test
    fun errorsFallThroughOnDeviceThenAndroidSpeechThenWhisper() {
        val auto = snap(onDevice = true, system = true, whisper = true)
        assertEquals(
            EngineChoice.SYSTEM,
            EngineSelector.fallback(EngineChoice.ON_DEVICE, auto, SpeechErrors.NETWORK),
        )
        assertEquals(
            EngineChoice.WHISPER,
            EngineSelector.fallback(EngineChoice.SYSTEM, auto, SpeechErrors.LANGUAGE_UNAVAILABLE),
        )
        assertNull(EngineSelector.fallback(EngineChoice.WHISPER, auto, SpeechErrors.CLIENT))
    }

    @Test
    fun silenceAndPermissionErrorsDoNotChangeEngine() {
        val auto = snap(system = true, whisper = true)
        assertNull(EngineSelector.fallback(EngineChoice.SYSTEM, auto, SpeechErrors.NO_MATCH))
        assertNull(EngineSelector.fallback(EngineChoice.SYSTEM, auto, SpeechErrors.SPEECH_TIMEOUT))
        assertNull(EngineSelector.fallback(EngineChoice.ON_DEVICE, auto, SpeechErrors.INSUFFICIENT_PERMISSIONS))
        assertFalse(SpeechErrors.shouldFallBack(SpeechErrors.NO_MATCH))
        assertTrue(SpeechErrors.shouldFallBack(SpeechErrors.SERVER))
    }

    @Test
    fun androidSpeechSettingDoesNotFallBackToWhisper() {
        val forced = snap(preference = EnginePreference.SYSTEM, system = true, whisper = true)
        assertNull(EngineSelector.fallback(EngineChoice.SYSTEM, forced, SpeechErrors.NETWORK))
        assertEquals(
            EngineChoice.SYSTEM,
            EngineSelector.fallback(EngineChoice.ON_DEVICE, forced, SpeechErrors.CLIENT),
        )
    }

    @Test
    fun shortOrCancelledCapturesAreDropped() {
        assertFalse(CapturePolicy.shouldTranscribe(299, cancelled = false))
        assertTrue(CapturePolicy.shouldTranscribe(300, cancelled = false))
        assertFalse(CapturePolicy.shouldTranscribe(2_000, cancelled = true))
    }

    private fun snap(
        preference: EnginePreference = EnginePreference.AUTO,
        system: Boolean = false,
        onDevice: Boolean = false,
        whisper: Boolean = false,
        systemFailed: Boolean = false,
    ) = EngineSnapshot(preference, system, onDevice, whisper, systemFailed)
}
