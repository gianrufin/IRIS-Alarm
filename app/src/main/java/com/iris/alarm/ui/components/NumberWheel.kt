package com.iris.alarm.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter

/**
 * Snapping wheel of numbers, three rows tall with the selection in the middle.
 * Used for the hour and minute of the alarm editor, at display type size.
 *
 * The value is reported when scrolling settles rather than on every frame, so a
 * flick through 40 minutes writes one draft update instead of forty.
 */
@Composable
fun NumberWheel(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 84.dp,
    textStyle: TextStyle = MaterialTheme.typography.displayLarge,
) {
    val count = range.last - range.first + 1
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (value - range.first).coerceIn(0, count - 1),
    )
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    fun settledIndex(): Int = listState.firstVisibleItemIndex +
        if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0

    LaunchedEffect(listState, range.first) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { scrolling -> !scrolling }
            .collect { onValueChange(range.first + settledIndex().coerceIn(0, count - 1)) }
    }

    // The editor loads an existing alarm asynchronously, so [value] usually
    // arrives after the first composition — without this the wheel would sit on
    // whatever it was initialised with and an edit would silently reset the time.
    LaunchedEffect(value) {
        val target = (value - range.first).coerceIn(0, count - 1)
        if (!listState.isScrollInProgress && settledIndex() != target) {
            listState.animateScrollToItem(target)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(itemHeight * 3),
    ) {
        items(count) { index ->
            val number = range.first + index
            val isSelected = number == value
            // Neighbours stay legible but clearly secondary, and the selection
            // swells slightly as it lands so the snap is felt as well as seen.
            val alpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.22f,
                label = "wheelAlpha",
            )
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.82f,
                label = "wheelScale",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString().padStart(2, '0'),
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .alpha(alpha)
                        .scale(scale),
                )
            }
        }
    }
}
