package com.mrpool.eightball.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrpool.eightball.data.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The player's picture, round, or the eight ball when they have not set one.
 *
 * [stamp] is what makes a newly picked picture appear: the file on disk is not something
 * Compose can watch, so the profile carries a number that changes when it is written and
 * this reloads on it.
 */
@Composable
fun Avatar(
    stamp: Long,
    size: Dp = 52.dp,
    ring: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val store = remember { ProfileStore.get(context) }
    var picture by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    // Decoding touches the disk, so it stays off the frame thread.
    LaunchedEffect(stamp) {
        picture = withContext(Dispatchers.IO) { store.avatar()?.asImageBitmap() }
    }

    val shape = CircleShape
    val modifier = Modifier
        .size(size)
        .clip(shape)
        .then(if (ring != null) Modifier.border(BorderStroke(2.dp, ring), shape) else Modifier)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    val held = picture
    if (held != null) {
        Image(
            bitmap = held,
            contentDescription = "Profile picture",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(listOf(Color(0xFF14181C), Color(0xFF2B3238)))
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "8",
                color = Color.White,
                fontSize = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.displayLarge
            )
        }
    }
}
