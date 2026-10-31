# Hex for Android

A dictation keyboard. The keyboard is not a full keyboard: a record control, live status, and space, delete, return, and switch-keyboard keys. Speech is committed into the focused field.

## Build

Android SDK 36, build-tools 36.0.0, CMake 3.22.1, and NDK 27.2.12479018. Java 17+.

```sh
cd android
./gradlew assembleDebug testDebugUnitTest lint
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The first native build downloads whisper.cpp 1.7.6 into `android/third_party` (gitignored) and compiles it for `arm64-v8a` and `x86_64`.

## Install

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Hex and allow the microphone. Then enable the keyboard:

Settings → System → Languages & input → On-screen keyboard → Hex

Switch to Hex from the keyboard key while a text field is focused. `adb shell ime enable anomaly.hex/.ime.HexInputMethodService` then `adb shell ime set anomaly.hex/.ime.HexInputMethodService` does the same on a device you can debug.

## Speech

Android `SpeechRecognizer` is first. On API 31+ Hex uses `createOnDeviceSpeechRecognizer` when the device offers it, and otherwise the standard recognizer. Partials show on the keyboard; the final text is formatted (Polished, Casual, Notes, Verbatim, same rules as the iPhone app) and committed.

If that recognizer is missing or returns an engine error, Hex listens again with whisper.cpp. Silence, no match, and a missing microphone permission do not switch engines. Settings → Speech can force Android speech or the offline model.

whisper.cpp 1.7.6 is CPU-only (no GPU, no OpenMP), linked through JNI. The multilingual `ggml-tiny` or `ggml-base` weights are downloaded in the app from Hugging Face the first time you ask, and are not in this repo. Tiny is the default. A clip longer than 60 seconds is cut there. The offline path has no partial transcript; the keyboard shows “Listening” until you tap Done.
