package com.kosmos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kosmos.app.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint

import android.content.Intent
import com.kosmos.app.platform.share.ShareIntentHandler
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shareIntentHandler: ShareIntentHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request basic permissions
        val requiredPermissions = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR
        )
        val missingPermissions = requiredPermissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 100)
        }

        // Handle intent on cold start
        shareIntentHandler.handleIntent(intent)

        setContent {
            com.kosmos.app.ui.theme.KosmosTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.kosmos.app.ui.component.AuroraBackground {
                        MainScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Set the new intent so getIntent() returns it
        setIntent(intent)
        // Handle intent when app is already running
        shareIntentHandler.handleIntent(intent)
    }
}
