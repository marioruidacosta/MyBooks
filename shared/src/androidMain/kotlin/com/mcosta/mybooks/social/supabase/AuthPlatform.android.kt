package com.mcosta.mybooks.social.supabase

import io.github.jan.supabase.auth.AuthConfig

internal actual fun AuthConfig.disablePlatformLifecycle() {
    enableLifecycleCallbacks = false
}
