package anomaly.hex.speech;

final class WhisperLib {
    private WhisperLib() {}

    static native boolean nativeAvailable();

    static native long nativeInit(String path);

    static native String nativeTranscribe(
            long handle, float[] samples, int count, String language, int threads);

    static native void nativeFree(long handle);
}
