package com.mcosta.mybooks.social.supabase

import android.util.Log
import io.github.jan.supabase.auth.AuthConfig
import javax.net.ssl.SSLException

internal actual fun AuthConfig.disablePlatformLifecycle() {
    enableLifecycleCallbacks = false
}

internal actual fun isTlsFailure(e: Throwable): Boolean = generateSequence(e) { it.cause }.take(10).any { it is SSLException }

internal actual fun logSocialError(e: Throwable) {
    runCatching { Log.w("MyBooks", "Community request failed", e) }
}
