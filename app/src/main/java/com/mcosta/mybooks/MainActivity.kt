package com.mcosta.mybooks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mcosta.mybooks.ui.MyBooksNavHost
import com.mcosta.mybooks.ui.theme.MyBooksTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyBooksTheme {
                MyBooksNavHost()
            }
        }
    }
}
