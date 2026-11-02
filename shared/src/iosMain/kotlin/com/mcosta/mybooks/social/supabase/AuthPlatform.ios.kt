package com.mcosta.mybooks.social.supabase

import io.github.jan.supabase.auth.AuthConfig
import io.ktor.client.engine.darwin.DarwinHttpRequestException

internal actual fun AuthConfig.disablePlatformLifecycle() = Unit

/** NSURLError codes -1200…-1206: secure connection failed / certificate problems. */
internal actual fun isTlsFailure(e: Throwable): Boolean = generateSequence(e) { it.cause }.take(10)
    .any { (it as? DarwinHttpRequestException)?.origin?.code in -1206L..-1200L }

internal actual fun logSocialError(e: Throwable) {
    println("MyBooks: community request failed: $e")
}
