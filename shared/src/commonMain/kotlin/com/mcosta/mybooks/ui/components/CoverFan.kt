package com.mcosta.mybooks.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Covers overlapping left to right, the first on top; a placeholder cover when there are none. */
@Composable
fun CoverFan(covers: List<String>, modifier: Modifier = Modifier, coverWidth: Dp = 44.dp, step: Dp = 24.dp) {
    val shown = covers.ifEmpty { listOf(null) }
    Box(modifier.width(coverWidth + step * (shown.size - 1)).height(coverWidth * 1.5f)) {
        shown.forEachIndexed { i, url ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                shadowElevation = 3.dp,
                modifier = Modifier.offset(x = step * i).zIndex((shown.size - i).toFloat()).width(coverWidth).height(coverWidth * 1.5f),
            ) {
                BookCover(url, Modifier.fillMaxSize())
            }
        }
    }
}
