package dev.thorrija.clickwheel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2

/**
 * A faithful click-wheel: drag around the ring to scroll (one tick per step,
 * with haptics), tap the ring's four cardinal zones for MENU / prev / next /
 * play-pause, press the center to select.
 */
@Composable
fun ClickWheel(
    onSelect: () -> Unit,
    onMenu: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val wheelSize = 300.dp
    var lastAngle by remember { mutableFloatStateOf(0f) }
    val stepDegrees = 22f

    Box(
        modifier = modifier.size(wheelSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(wheelSize)
                // iridescent Y2K halo around the wheel
                .border(
                    4.dp,
                    Brush.sweepGradient(
                        listOf(HotPink, UltraViolet, CyberCyan, AcidLime, HotPink),
                    ),
                    CircleShape,
                )
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFDFDFF), Color(0xFFC9CFE6)),
                    ),
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            lastAngle =
                                angleAt(offset.x - size.width / 2f, offset.y - size.height / 2f)
                        },
                    ) { change, _ ->
                        change.consume()
                        val now = angleAt(
                            change.position.x - size.width / 2f,
                            change.position.y - size.height / 2f,
                        )
                        var delta = now - lastAngle
                        if (delta > 180) delta -= 360
                        if (delta < -180) delta += 360
                        lastAngle = now
                        if (kotlin.math.abs(delta) >= stepDegrees / 2f) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (delta > 0) onNext() else onPrevious()
                        }
                    }
                },
        )

        // Ring buttons (cardinal zones of the classic wheel)
        WheelButton(Modifier.align(Alignment.TopCenter), "MENU", InkText, FontWeight.Black) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress); onMenu()
        }
        WheelButton(Modifier.align(Alignment.CenterStart), "⏮", CyberCyan, FontWeight.Bold) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress); onPrevious()
        }
        WheelButton(Modifier.align(Alignment.CenterEnd), "⏭", CyberCyan, FontWeight.Bold) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress); onNext()
        }
        WheelButton(Modifier.align(Alignment.BottomCenter), "▶/❙❙", UltraViolet, FontWeight.Bold) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress); onPlayPause()
        }

        Box(
            modifier = Modifier
                .size(96.dp)
                .shadow(4.dp, CircleShape)
                .border(3.dp, Brush.linearGradient(listOf(HotPink, CyberCyan)), CircleShape)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color.White, Color(0xFFE4E9FF))))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onSelect() })
                },
            contentAlignment = Alignment.Center,
        ) {}
    }
}

/** Small tappable zone sitting on top of the wheel ring. */
@Composable
private fun WheelButton(
    modifier: Modifier,
    label: String,
    tint: Color,
    weight: FontWeight,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .offset(y = 0.dp)
            .size(72.dp, 44.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = tint, fontWeight = weight, fontSize = 15.sp)
    }
}

private fun angleAt(dx: Float, dy: Float): Float =
    Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
