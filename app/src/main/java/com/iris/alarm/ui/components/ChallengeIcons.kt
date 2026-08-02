package com.iris.alarm.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.iris.alarm.domain.model.VisionChallenge

/** One glyph per challenge, shared by the alarm list and the editor. */
fun VisionChallenge.challengeIcon(): ImageVector = when (this) {
    VisionChallenge.SMILE -> Icons.Rounded.SentimentSatisfiedAlt
    VisionChallenge.OBJECT_HUNT -> Icons.Rounded.CenterFocusStrong
    VisionChallenge.LUMEN -> Icons.Rounded.LightMode
}
