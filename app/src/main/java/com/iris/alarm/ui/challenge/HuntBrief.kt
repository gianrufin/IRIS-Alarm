package com.iris.alarm.ui.challenge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iris.alarm.domain.model.HuntTarget

/**
 * What to go and find, and where to start looking.
 *
 * The room hint matters more than it sounds: the object is drawn at random, so
 * the first half-second of the challenge is spent working out where such a thing
 * would even be, and that is not thinking anyone is good at immediately after
 * waking up.
 *
 * "NOT IN HERE" is the honest answer to a pool of objects that cannot possibly
 * suit every home — but it is limited, because an unlimited re-roll is an off
 * switch with extra steps.
 */
@Composable
fun HuntBrief(
    target: HuntTarget,
    swapsLeft: Int,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "GO FIND",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // A swap should feel like a new card being dealt, not text being edited.
        AnimatedContent(
            targetState = target,
            transitionSpec = {
                (fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.85f))
                    .togetherWith(fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.1f))
            },
            label = "huntTarget",
        ) { current ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = current.display.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "TRY THE ${current.where.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (swapsLeft > 0) {
            Text(
                text = "NOT IN HERE? SWAP ($swapsLeft LEFT)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onSwap)
                    .padding(top = 6.dp, bottom = 2.dp),
            )
        } else {
            Text(
                text = "NO SWAPS LEFT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            )
        }
    }
}
