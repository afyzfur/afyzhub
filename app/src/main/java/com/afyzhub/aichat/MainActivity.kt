package com.afyzhub.aichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.afyzhub.aichat.ui.AfyzhubApp
import com.afyzhub.aichat.ui.theme.afyzhubTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            afyzhubTheme {
                AfyzhubApp(viewModel)
            }
        }
    }
}