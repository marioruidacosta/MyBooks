package com.mcosta.mybooks.social.supabase

import io.github.jan.supabase.auth.AuthConfig

/** Turns off Android's app-lifecycle session hooks (they need a main looper, absent in host tests). */
internal expect fun AuthConfig.disablePlatformLifecycle()
