package com.mcosta.mybooks

import android.app.Application
import com.mcosta.mybooks.social.SocialConfig

class MyBooksApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = androidAppContainer(
            this,
            BuildConfig.GOOGLE_BOOKS_API_KEY,
            BuildConfig.VERSION_NAME,
            SocialConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
        )
    }
}
