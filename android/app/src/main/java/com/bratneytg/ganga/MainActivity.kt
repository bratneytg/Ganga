package com.bratneytg.ganga

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.bratneytg.ganga.ui.EditScreen
import com.bratneytg.ganga.ui.GangaTheme
import com.bratneytg.ganga.ui.HomeScreen
import com.bratneytg.ganga.ui.MainViewModel
import com.bratneytg.ganga.ui.PrefsScreen
import com.bratneytg.ganga.ui.Screen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* sin permiso, Ganga sigue buscando pero no avisa */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            GangaTheme {
                val snackbar = remember { SnackbarHostState() }
                val screen = vm.screen
                val message = vm.message

                LaunchedEffect(message) {
                    if (message != null) {
                        snackbar.showSnackbar(message)
                        vm.message = null
                    }
                }
                BackHandler(enabled = screen !is Screen.Home) { vm.screen = Screen.Home }

                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .systemBarsPadding(),
                ) {
                    when (screen) {
                        is Screen.Home -> HomeScreen(vm)
                        is Screen.Edit -> EditScreen(vm, screen)
                        is Screen.Prefs -> PrefsScreen(vm)
                    }
                    SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Un enlace compartido desde el navegador o una app de tienda abre el editor con el texto relleno. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        vm.handleShare(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
