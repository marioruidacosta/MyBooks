package com.mcosta.mybooks.social.supabase

import io.github.jan.supabase.auth.AuthConfig

/** Turns off Android's app-lifecycle session hooks (they need a main looper, absent in host tests). */
internal expect fun AuthConfig.disablePlatformLifecycle()

/** True if [e] (or a cause) is a TLS/certificate failure, e.g. a network that intercepts HTTPS. */
internal expect fun isTlsFailure(e: Throwable): Boolean

/** Writes a failed social call to the platform log (Android: logcat tag "MyBooks"). */
internal expect fun logSocialError(e: Throwable)
