package com.iris.alarm.ui.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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

    LaunchedEffect(listState, range.first) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { scrolling -> !scrolling }
            .collect {
                val settled = listState.firstVisibleItemIndex +
                    if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
                onValueChange(range.first + settled.coerceIn(0, count - 1))
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
                    // Neighbours stay legible but clearly secondary to the selection.
                    modifier = Modifier.alpha(if (number == value) 1f else 0.25f),
                )
            }
        }
    }
}
