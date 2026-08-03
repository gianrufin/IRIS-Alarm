package com.iris.alarm.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.flow.filter

/**
 * An endless, turntable-style number wheel.
 *
 * The list is a very large virtual range whose indices wrap onto the real range,
 * so scrolling never hits an end — spin past 59 minutes and it keeps going into
 * 00. Items away from the centre are pushed back in Z, shrunk and faded, which
 * reads as a physical drum rotating rather than a flat list sliding.
 *
 * [onWrap] fires each time the wheel crosses the boundary of the real range,
 * which is what lets an hour wheel flip AM to PM when it rolls past 12.
 */
@Composable
fun TurntableWheel(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 76.dp,
    textStyle: TextStyle = MaterialTheme.typography.displayMedium,
    onWrap: ((forward: Boolean) -> Unit)? = null,
) {
    val span = range.last - range.first + 1

    // A large multiple of the span, so the wheel can be spun for a long time
    // before it runs out, and the midpoint is span-aligned so index maths stays
    // exact wherever the user starts from.
    val virtualCount = remember(span) { span * VIRTUAL_CYCLES }
    val midpoint = remember(span) { (virtualCount / 2) - (virtualCount / 2) % span }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = midpoint + (value - range.first).coerceIn(0, span - 1),
    )
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    fun centreIndex(): Int = listState.firstVisibleItemIndex +
        if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0

    // Tracks which "revolution" of the wheel we are on, so a crossing can be
    // told apart from a jump.
    val cycle = remember { intArrayOf(centreIndex().floorDiv(span)) }

    LaunchedEffect(listState, span) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { scrolling -> !scrolling }
            .collect {
                val index = centreIndex()
                val newCycle = index.floorDiv(span)
                if (newCycle != cycle[0]) {
                    onWrap?.invoke(newCycle > cycle[0])
                    cycle[0] = newCycle
                }
                onValueChange(range.first + index.mod(span))
            }
    }

    // The value can also change from outside — loading a saved alarm, or the
    // AM/PM toggle rewriting the hour — so the wheel follows it without
    // fighting a drag in progress.
    LaunchedEffect(value) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        val index = centreIndex()
        val current = range.first + index.mod(span)
        if (current == value) return@LaunchedEffect

        // Move to the nearest index showing the target, so the wheel takes the
        // short way round instead of unwinding to the middle.
        val delta = (value - current).let { raw ->
            when {
                raw > span / 2 -> raw - span
                raw < -span / 2 -> raw + span
            else -> raw
            }
        }
        listState.animateScrollToItem((index + delta).coerceIn(0, virtualCount - 1))
        cycle[0] = (index + delta).floorDiv(span)
    }

    LazyColumn(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(itemHeight * 3),
    ) {
        items(virtualCount) { index ->
            val number = range.first + index.mod(span)
            TurntableItem(
                text = number.toString().padStart(2, '0'),
                itemHeight = itemHeight,
                textStyle = textStyle,
                distanceFromCentre = {
                    // Recomputed on every frame from live scroll state, so the
                    // rotation tracks the finger rather than snapping per item.
                    val centre = listState.firstVisibleItemIndex +
                        listState.firstVisibleItemScrollOffset / itemHeightPx
                    index - centre - 1f
                },
            )
        }
    }
}

@Composable
private fun TurntableItem(
    text: String,
    itemHeight: Dp,
    textStyle: TextStyle,
    distanceFromCentre: () -> Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = textStyle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.graphicsLayer {
                val distance = distanceFromCentre()
                val magnitude = abs(distance).coerceAtMost(1.6f)

                // Tilting around X and pushing back in Z is what makes it read as
                // a drum. cameraDistance keeps the perspective from looking bent.
                rotationX = distance * -MAX_ROTATION_DEGREES
                cameraDistance = 12f * density
                scaleX = 1f - magnitude * 0.18f
                scaleY = 1f - magnitude * 0.18f
                alpha = (1f - magnitude * 0.62f).coerceIn(0.12f, 1f)
            },
        )
    }
}

/**
 * The AM/PM pill beside the hour wheel. Flips with a half-turn, so a wrap of the
 * hour wheel past 12 is visibly the same motion as tapping it.
 */
@Composable
fun MeridiemToggle(
    isPm: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flip by animateFloatAsState(
        targetValue = if (isPm) 180f else 0f,
        label = "meridiemFlip",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
            .clickable { onChange(!isPm) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isPm) "PM" else "AM",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer {
                rotationX = flip
                cameraDistance = 14f * density
                // Without this the face reads upside down at the half turn.
                if (isPm) rotationX = flip - 180f
            },
        )
    }
}

private const val VIRTUAL_CYCLES = 2001
private const val MAX_ROTATION_DEGREES = 38f
