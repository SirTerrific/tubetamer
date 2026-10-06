package com.sirterrific.tubetamer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirterrific.tubetamer.ui.AppRoot
import com.sirterrific.tubetamer.ui.AppViewModel
import com.sirterrific.tubetamer.ui.TubeTamerTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        AppViewModel.factory((application as TubeTamerApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TubeTamerTheme {
                val screen by vm.screen.collectAsStateWithLifecycle()
                AppRoot(vm, screen)
            }
        }
    }
}
