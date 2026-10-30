package com.mcosta.mybooks

import android.app.Application

class MyBooksApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
