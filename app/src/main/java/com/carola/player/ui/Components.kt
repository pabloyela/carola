package com.carola.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.carola.player.data.CoverRepository
import com.carola.player.data.CoverRequest
import java.io.File

val LocalCovers = staticCompositionLocalOf<CoverRepository> { error("CoverRepository no disponible") }

private val placeholderBrush = Brush.linearGradient(listOf(Color(0xFF2A2C35), Color(0xFF16171C)))

/** Muestra una imagen de archivo o un disco de relleno si no hay portada. */
@Composable
fun FileImage(file: File?, modifier: Modifier = Modifier) {
    Box(modifier.background(placeholderBrush), contentAlignment = Alignment.Center) {
        if (file == null) {
            Icon(
                Icons.Rounded.Album, contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxSize(0.42f),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(file).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Portada de álbum que se carga (o descarga) sola. */
@Composable
fun CoverArt(request: CoverRequest, modifier: Modifier = Modifier) {
    val repo = LocalCovers.current
    val file by produceState(initialValue = repo.peek(request.key), request.key) {
        if (value == null) value = repo.cover(request)
    }
    FileImage(file, modifier)
}

@Composable
fun BigIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp = 64.dp,
    tint: Color = LocalContentColor.current,
    background: Color = Color.Transparent,
    iconScale: Float = 0.55f,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * iconScale))
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BigIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, size = 56.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle, fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, action: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(84.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        action()
    }
}
