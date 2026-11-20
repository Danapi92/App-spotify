package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.AppPreferences
import com.example.data.repository.SettingsRepository
import com.example.ui.AuthenticatedStreamApp
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.localization.EnglishStrings
import com.example.ui.localization.IndonesianStrings
import com.example.ui.localization.LocalStrings
import com.example.ui.theme.StreamMusicTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamMusicApp()
        }
    }
}

@Composable
fun StreamMusicApp() {
    val context = LocalContext.current
    val settingsRepo = remember(context) { SettingsRepository(context.applicationContext) }
    val preferences by settingsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = AppPreferences())
    val strings = if (preferences.languageCode == "en") EnglishStrings else IndonesianStrings

    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    CompositionLocalProvider(LocalStrings provides strings) {
        StreamMusicTheme(darkTheme = preferences.darkTheme) {
            val user = currentUser
            if (user == null) {
                AuthScreen(onAuthSuccess = { })
            } else {
                key(user.uid) {
                    AuthenticatedStreamApp(
                        currentUserId = user.uid,
                        settingsRepo = settingsRepo
                    )
                }
            }
        }
    }
}
