package com.siyandimitrov.pocketindex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.siyandimitrov.pocketindex.ui.PocketIndexApp
import com.siyandimitrov.pocketindex.ui.theme.PocketIndexTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketIndexTheme {
                PocketIndexApp()
            }
        }
    }
}

