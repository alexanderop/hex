package anomaly.hex.speech

import java.util.concurrent.atomic.AtomicBoolean

internal object MicLock {
    private val held = AtomicBoolean(false)

    fun tryAcquire(): Boolean = held.compareAndSet(false, true)

    fun release() {
        held.set(false)
    }
}
