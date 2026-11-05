package com.sirterrific.tubetamer.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirterrific.tubetamer.mobile.ui.MobileTheme
import com.sirterrific.tubetamer.mobile.ui.MobileRoot
import com.sirterrific.tubetamer.ui.AppViewModel
import com.sirterrific.tubetamer.ui.ServerLanguage

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        AppViewModel.factory((application as MobileApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val locale by vm.locale.collectAsStateWithLifecycle()
            ServerLanguage(locale) {
                MobileTheme {
                    val screen by vm.screen.collectAsStateWithLifecycle()
                    MobileRoot(vm, screen)
                }
            }
        }
    }
}
