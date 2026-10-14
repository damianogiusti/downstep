package com.damianogiusti.slowdowner.ui.common

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

/**
 * Integer slider over 0..[max]. While the user drags it shows its own value, because [value]
 * keeps arriving from the model (every 100 ms during playback) and would make the thumb jump.
 * With [commitOnRelease] the value is reported only when the finger lifts: needed for
 * seeking, where every intermediate jump is audible as a stutter.
 */
@Composable
fun ValueSlider(
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    commitOnRelease: Boolean = false,
    colors: SliderColors = SliderDefaults.colors(),
) {
    var dragValue by remember { mutableStateOf<Int?>(null) }
    Slider(
        value = (dragValue ?: value).toFloat(),
        onValueChange = {
            val position = it.roundToInt()
            dragValue = position
            if (!commitOnRelease) onValueChange(position)
        },
        onValueChangeFinished = {
            if (commitOnRelease) dragValue?.let(onValueChange)
            dragValue = null
        },
        valueRange = 0f..max.toFloat(),
        enabled = enabled,
        colors = colors,
        modifier = modifier,
    )
}
