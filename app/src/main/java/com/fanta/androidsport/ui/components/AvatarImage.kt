package com.fanta.androidsport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.fanta.androidsport.ui.icons.steps
import com.fanta.androidsport.ui.theme.ElectricBlue
import com.fanta.androidsport.utils.base64ToImageBitmap

@Composable
fun AvatarImage(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    placeholderColor: Color = ElectricBlue,
    placeholderIcon: ImageVector = steps
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = placeholderIcon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(0.5f),
            tint = placeholderColor
        )

        if (avatarUrl != null && avatarUrl.isNotEmpty()) {
            if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://") || avatarUrl.startsWith("content://") || avatarUrl.startsWith("file://")) {
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Base64 fallback for backward compatibility
                val bitmap = remember(avatarUrl) { base64ToImageBitmap(avatarUrl) }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
