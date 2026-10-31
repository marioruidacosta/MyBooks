package com.mcosta.mybooks

import android.app.Application

class MyBooksApp : Application(), ContainerHolder {
    override lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, BuildConfig.GOOGLE_BOOKS_API_KEY, BuildConfig.VERSION_NAME)
    }
}
