package com.mcosta.mybooks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mcosta.mybooks.platform.androidPlatformServices
import com.mcosta.mybooks.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MyBooksApp).container
        val services = androidPlatformServices(this)
        setContent {
            App(container, services)
        }
    }
}
