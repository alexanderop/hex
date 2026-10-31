package anomaly.hex

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import anomaly.hex.ui.HexColors
import anomaly.hex.ui.HexTheme
import anomaly.hex.ui.HomeScreen
import anomaly.hex.ui.HomeViewModel
import anomaly.hex.ui.OnboardingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF0A0B0D.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF0A0B0D.toInt()),
        )
        setContent {
            val model: HomeViewModel = viewModel()
            val permission = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { model.refresh() }
            var onboarded by remember { mutableStateOf(model.settings.onboardingComplete()) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) model.refresh()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                model.refresh()
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            HexTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(HexColors.BgDeep)
                        .systemBarsPadding()
                        .imePadding(),
                ) {
                    if (!onboarded) {
                        OnboardingScreen(
                            micGranted = model.micGranted,
                            keyboardEnabled = model.keyboardEnabled,
                            language = model.settings.language(),
                            onAllowMic = { permission.launch(Manifest.permission.RECORD_AUDIO) },
                            onOpenKeyboardSettings = {
                                startActivity(android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS))
                            },
                            onLanguage = { model.settings.setLanguage(it.tag) },
                            onFinished = {
                                model.settings.setOnboardingComplete()
                                onboarded = true
                            },
                        )
                    } else {
                        HomeScreen(model, onRequestMic = { permission.launch(Manifest.permission.RECORD_AUDIO) })
                    }
                }
            }
        }
    }
}
