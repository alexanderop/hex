package anomaly.hex.ime

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import anomaly.hex.MainActivity
import anomaly.hex.data.HexSettings
import anomaly.hex.logic.TranscriptFormatter
import anomaly.hex.speech.DictationSession
import anomaly.hex.speech.WhisperModelStore
import anomaly.hex.ui.HexKeyboard
import anomaly.hex.ui.HexTheme
import anomaly.hex.ui.KeyboardPresenter

class HexInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private val presenter = KeyboardPresenter()

    private var sessionCreated = false
    private val settings by lazy { HexSettings(this) }
    private val session by lazy {
        sessionCreated = true
        DictationSession(this, settings, WhisperModelStore(this)).also { created ->
            created.onFinalText = { raw -> commitDictation(raw) }
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore
        get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        installViewTreeOwners()
    }

    override fun onCreateInputView(): View {
        refresh()
        if (lifecycleRegistry.currentState == Lifecycle.State.CREATED) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }
        installViewTreeOwners()
        val compose = ComposeView(this)
        compose.setViewTreeLifecycleOwner(this)
        compose.setViewTreeViewModelStoreOwner(this)
        compose.setViewTreeSavedStateRegistryOwner(this)
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        compose.setContent {
            HexTheme {
                HexKeyboard(
                    session = session,
                    settings = settings,
                    presenter = presenter,
                    onDictate = session::start,
                    onAllowMic = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    onStyle = settings::setStyle,
                    onSpace = { currentInputConnection?.commitText(" ", 1) },
                    onDelete = { currentInputConnection?.deleteSurroundingTextInCodePoints(1, 0) },
                    onReturn = { currentInputConnection?.commitText("\n", 1) },
                    onSwitchKeyboard = { switchKeyboard() },
                )
            }
        }
        val height = keyboardHeightPx()
        val frame = FixedHeightLayout(this, height, this)
        frame.addView(
            compose,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        return frame
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }
        presenter.returnLabel = returnLabel(info)
        refresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        session.cancel()
        super.onFinishInputView(finishingInput)
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // A hardware keyboard must not hide Hex. The user picked this keyboard to dictate.
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onDestroy() {
        if (sessionCreated) session.close()
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
        store.clear()
        super.onDestroy()
    }

    private fun keyboardHeightPx(): Int {
        val density = resources.displayMetrics.density
        val nav = if (Build.VERSION.SDK_INT >= 30) {
            window?.window?.decorView?.rootWindowInsets
                ?.getInsets(WindowInsets.Type.navigationBars())
                ?.bottom ?: 0
        } else {
            0
        }
        val fallbackNav = if (nav > 0) nav else (48 * density).toInt()
        return (168 * density).toInt() + fallbackNav
    }

    private fun installViewTreeOwners() {
        val decor = window?.window?.decorView ?: return
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
    }

    private fun refresh() {
        presenter.micGranted = session.microphoneGranted()
        presenter.revision += 1
    }

    private fun commitDictation(raw: String) {
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(80, 0)?.toString()
        val after = connection.getTextAfterCursor(80, 0)?.toString()
        val formatted = TranscriptFormatter.format(raw, settings.style())
        val text = TranscriptFormatter.insertionText(formatted, before, after)
        if (text.isNotEmpty()) connection.commitText(text, 1)
    }

    private fun switchKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        val token = window?.window?.attributes?.token
        if (token != null && imm.switchToNextInputMethod(token, false)) return
        imm.showInputMethodPicker()
    }

    private class FixedHeightLayout(
        context: Context,
        private val heightPx: Int,
        private val owner: HexInputMethodService,
    ) : FrameLayout(context) {
        override fun onAttachedToWindow() {
            rootView.setViewTreeLifecycleOwner(owner)
            rootView.setViewTreeViewModelStoreOwner(owner)
            rootView.setViewTreeSavedStateRegistryOwner(owner)
            super.onAttachedToWindow()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val exact = MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY)
            super.onMeasure(widthMeasureSpec, exact)
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), heightPx)
        }
    }

    private fun returnLabel(info: EditorInfo?): String = when (info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_SEND -> "Send"
        EditorInfo.IME_ACTION_DONE -> "Done"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        else -> "Return"
    }
}
