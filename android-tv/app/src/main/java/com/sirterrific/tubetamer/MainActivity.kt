package com.sirterrific.tubetamer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sirterrific.tubetamer.ui.HomePlaceholder
import com.sirterrific.tubetamer.ui.TubeTamerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TubeTamerTheme {
                HomePlaceholder()
            }
        }
    }
}
