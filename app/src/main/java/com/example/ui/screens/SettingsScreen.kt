package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AudioQuality
import com.example.data.repository.AppPreferences
import com.example.ui.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onBack: () -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectQuality: (AudioQuality) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit
) {
    BackHandler { onBack() }

    val strings = LocalStrings.current
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> onToggleNotifications(granted) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        TopAppBar(
            title = { Text(strings.settingsTitle, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dark Mode
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = strings.darkMode,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = preferences.darkTheme,
                            onCheckedChange = onToggleDarkTheme,
                            modifier = Modifier.testTag("settings_dark_mode_switch")
                        )
                    }
                }
            }

            // Language (Indonesian / English)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(strings.languageSetting, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = preferences.languageCode == "id",
                                onClick = { onSelectLanguage("id") },
                                label = { Text("Bahasa Indonesia") }
                            )
                            FilterChip(
                                selected = preferences.languageCode == "en",
                                onClick = { onSelectLanguage("en") },
                                label = { Text("English") }
                            )
                        }
                    }
                }
            }

            // Audio Quality
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(strings.audioQuality, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                AudioQuality.AUTO to strings.qualityAuto,
                                AudioQuality.STANDARD to strings.qualityStandard,
                                AudioQuality.HIGH to strings.qualityHigh
                            ).forEach { (q, label) ->
                                FilterChip(
                                    selected = preferences.audioQuality == q,
                                    onClick = { onSelectQuality(q) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // Notifications
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = strings.notificationsSetting,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = preferences.notificationsEnabled,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onToggleNotifications(wantEnable)
                                }
                            }
                        )
                    }
                }
            }

            // Equalizer
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenEqualizer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(strings.equalizer, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Privacy Policy
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showPrivacyDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(strings.privacyPolicy, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Terms of Service
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showTermsDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(strings.termsOfService, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(strings.privacyPolicy) },
            text = {
                Text(
                    if (preferences.languageCode == "id") {
                        "STREAM MUSIC menyimpan profil akun, playlist, lagu favorit, riwayat pemutaran, dan daftar perangkat secara aman di Cloud Firestore dengan isolasi per pengguna. Tidak ada data pribadi Anda yang dijual atau dibagikan ke pihak ketiga."
                    } else {
                        "STREAM MUSIC securely stores your account profile, playlists, favorite tracks, playback history, and registered devices in Cloud Firestore with strict per-user isolation. We never sell or share your personal data with third parties."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("OK") }
            }
        )
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text(strings.termsOfService) },
            text = {
                Text(
                    if (preferences.languageCode == "id") {
                        "Seluruh konten audio di STREAM MUSIC berasal dari API musik legal berizin (Audius Open Discovery API, Deezer Public Previews, LRCLIB, dan katalog cloud resmi). Unduhan offline hanya tersedia untuk karya yang diberi izin unduh oleh penciptanya."
                    } else {
                        "All audio content in STREAM MUSIC is streamed from licensed public music APIs (Audius Open Discovery API, Deezer Public Previews, LRCLIB, and official cloud catalog). Offline downloads are strictly limited to tracks explicitly licensed for download by their creators."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) { Text("OK") }
            }
        )
    }
}
