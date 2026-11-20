package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.UiState
import com.example.data.model.UserDeviceDoc
import com.example.data.model.UserProfileDoc
import com.example.ui.components.StreamArtwork
import com.example.ui.localization.LocalStrings
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonMint

@Composable
fun ProfileScreen(
    userProfileState: UiState<UserProfileDoc?>,
    devicesState: UiState<List<UserDeviceDoc>>,
    favoritesCount: Int,
    playlistsCount: Int,
    downloadsCount: Int,
    onEditName: (String) -> Unit,
    onPhotoSelected: (String) -> Unit,
    onTogglePremium: (Boolean) -> Unit,
    onRegisterDevice: () -> Unit,
    onRemoveDevice: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onSignOut: () -> Unit
) {
    val strings = LocalStrings.current
    val profile = (userProfileState as? UiState.Success)?.data
    val devices = (devicesState as? UiState.Success)?.data.orEmpty()
    val isPremium = profile?.subscriptionTier == "premium"

    var showEditNameDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                onPhotoSelected(uri.toString())
            }
        }
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.profileTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = strings.settingsTitle)
                }
            }
        }

        // User Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    ) {
                        StreamArtwork(
                            url = profile?.photoUrl.orEmpty(),
                            contentDescription = profile?.displayName ?: "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            isCircular = true
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = strings.changeAvatar,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile?.displayName ?: "Stream User",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { showEditNameDialog = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = strings.editDisplayName,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (!profile?.email.isNullOrBlank()) {
                            Text(
                                text = profile!!.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (isPremium) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = if (isPremium) strings.premiumMember else strings.freePlan,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPremium) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(label = strings.favoritesTab, count = favoritesCount, modifier = Modifier.weight(1f))
                StatTile(label = strings.playlistsTab, count = playlistsCount, modifier = Modifier.weight(1f))
                StatTile(label = strings.downloadsTab, count = downloadsCount, modifier = Modifier.weight(1f))
            }
        }

        // Premium Experience Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    ElectricViolet.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    NeonMint.copy(alpha = 0.22f)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.WorkspacePremium,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPremium) strings.premiumBenefitsTitle else strings.premiumMember,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = isPremium,
                            onCheckedChange = onTogglePremium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (strings.languageCode == "id") {
                            "Nikmati audio bebas iklan, streaming hingga 320 kbps, unduhan offline berizin, Equalizer perangkat keras, dan sinkronisasi multi-perangkat."
                        } else {
                            "Ad-free listening, up to 320 kbps audio streaming, licensed offline downloads, hardware Equalizer, and multi-device sync."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onOpenEqualizer) {
                        Icon(Icons.Rounded.Equalizer, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.equalizer)
                    }
                }
            }
        }

        // Device Management
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.deviceManagement,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = onRegisterDevice) {
                    Text(strings.registerThisDevice)
                }
            }
        }

        items(devices, key = { it.id }) { device ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(device.deviceName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = device.platform,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onRemoveDevice(device.id) }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Remove device",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Sign Out
        item {
            Button(
                onClick = onSignOut,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.signOut, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showEditNameDialog) {
        var nameDraft by remember(profile?.displayName) {
            mutableStateOf(profile?.displayName.orEmpty())
        }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(strings.editDisplayName) },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { if (it.length <= 80) nameDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameDraft.trim().isNotEmpty()) {
                            onEditName(nameDraft.trim())
                            showEditNameDialog = false
                        }
                    }
                ) {
                    Text(strings.saveChanges)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
