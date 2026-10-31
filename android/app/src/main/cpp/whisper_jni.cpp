#include <jni.h>

#include <string>
#include <vector>

#include <android/log.h>

#ifdef HEX_HAS_WHISPER
#include "whisper.h"
#endif

namespace {

constexpr const char *kTag = "HexWhisper";

}

extern "C" JNIEXPORT jboolean JNICALL
Java_anomaly_hex_speech_WhisperLib_nativeAvailable(JNIEnv *, jclass) {
#ifdef HEX_HAS_WHISPER
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_anomaly_hex_speech_WhisperLib_nativeInit(JNIEnv *env, jclass, jstring path) {
#ifdef HEX_HAS_WHISPER
    const char *cpath = env->GetStringUTFChars(path, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(cpath, params);
    env->ReleaseStringUTFChars(path, cpath);
    if (ctx == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "whisper_init failed");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
#else
    (void) env;
    (void) path;
    return 0;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_anomaly_hex_speech_WhisperLib_nativeTranscribe(
    JNIEnv *env,
    jclass,
    jlong handle,
    jfloatArray samples,
    jint count,
    jstring language,
    jint threads
) {
#ifdef HEX_HAS_WHISPER
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr || samples == nullptr || count <= 0) {
        return env->NewStringUTF("");
    }
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    const char *langChars = env->GetStringUTFChars(language, nullptr);
    std::string lang = langChars == nullptr ? "en" : langChars;
    env->ReleaseStringUTFChars(language, langChars);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.language = lang.c_str();
    params.n_threads = threads > 0 ? threads : 2;

    const int rc = whisper_full(ctx, params, data, count);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    if (rc != 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "whisper_full failed (%d)", rc);
        return nullptr;
    }

    std::string text;
    const int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; i++) {
        const char *segment = whisper_full_get_segment_text(ctx, i);
        if (segment == nullptr || segment[0] == '\0') continue;
        if (!text.empty() && text.back() != ' ' && segment[0] != ' ') text.push_back(' ');
        text += segment;
    }
    return env->NewStringUTF(text.c_str());
#else
    (void) handle;
    (void) samples;
    (void) count;
    (void) language;
    (void) threads;
    return env->NewStringUTF("");
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_anomaly_hex_speech_WhisperLib_nativeFree(JNIEnv *, jclass, jlong handle) {
#ifdef HEX_HAS_WHISPER
    if (handle != 0) whisper_free(reinterpret_cast<whisper_context *>(handle));
#else
    (void) handle;
#endif
}
