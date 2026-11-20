package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.player.EqualizerUiState
import com.example.ui.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    state: EqualizerUiState,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onBandChange: (Int, Int) -> Unit
) {
    val strings = LocalStrings.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.equalizer,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (state.supported) {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = onToggleEnabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!state.supported) {
                Text(
                    text = if (strings.languageCode == "id") {
                        "Putar lagu terlebih dahulu atau perangkat Anda tidak mendukung Equalizer perangkat keras."
                    } else {
                        "Start playing a track first, or your audio hardware does not expose Equalizer controls."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 28.dp)
                )
            } else {
                if (state.presets.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(state.presets) { index, name ->
                            FilterChip(
                                selected = index == state.selectedPreset,
                                onClick = { onSelectPreset(index) },
                                label = { Text(name) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                state.bands.forEach { band ->
                    val freqLabel = if (band.centerFreqHz >= 1000) {
                        "${band.centerFreqHz / 1000} kHz"
                    } else {
                        "${band.centerFreqHz} Hz"
                    }
                    val min = state.minBandLevelMb.toFloat()
                    val max = state.maxBandLevelMb.toFloat().coerceAtLeast(min + 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = freqLabel,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(0.24f)
                        )
                        Slider(
                            value = band.levelMb.toFloat(),
                            onValueChange = { onBandChange(band.index, it.toInt()) },
                            valueRange = min..max,
                            enabled = state.enabled,
                            modifier = Modifier.weight(0.76f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
