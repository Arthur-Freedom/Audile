package com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.domain.preferences.AudioCaptureMode
import com.mrsep.musicrecognizer.core.ui.theme.PlusColors
import com.mrsep.musicrecognizer.feature.recognition.R
import com.mrsep.musicrecognizer.core.strings.R as StringsR

/**
 * Audile+: picks what a tap on the big button listens to, the microphone or the phone's own audio,
 * and says so in a toast, like Shazam's switch.
 */
@Composable
internal fun CaptureModeSwitch(
    selected: AudioCaptureMode?,
    onSelect: (AudioCaptureMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(PlusColors.Frost)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        Segment(
            AudioCaptureMode.Microphone, R.drawable.plus_mic_24, StringsR.string.audio_capture_mode_microphone,
            hint = "Music around you", selected = selected, onSelect = onSelect,
        )
        Segment(
            AudioCaptureMode.Device, R.drawable.plus_headphones_24, StringsR.string.audio_capture_mode_device,
            hint = "Music on this phone", selected = selected, onSelect = onSelect,
        )
    }
}

@Composable
private fun Segment(
    mode: AudioCaptureMode,
    @DrawableRes icon: Int,
    label: Int,
    hint: String,
    selected: AudioCaptureMode?,
    onSelect: (AudioCaptureMode) -> Unit,
) {
    val context = LocalContext.current
    val isSelected = mode == selected
    val background by animateColorAsState(if (isSelected) Color.White else Color.Transparent, label = "segmentBackground")
    val tint by animateColorAsState(if (isSelected) PlusColors.SkyTop else Color.White, label = "segmentTint")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = {
                onSelect(mode)
                Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
            })
            .padding(horizontal = 26.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = stringResource(label), tint = tint, modifier = Modifier.size(26.dp))
    }
}
