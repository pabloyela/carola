package com.carola.player.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.carola.player.MainViewModel
import com.carola.player.PlaybackUi
import com.carola.player.data.formatTime
import java.io.File

@Composable
fun NowPlayingScreen(vm: MainViewModel, onBrowse: () -> Unit) {
    val pb by vm.playback.collectAsStateWithLifecycle()
    val cover by vm.cover.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }

    if (!pb.hasMedia) {
        EmptyState(
            Icons.Rounded.LibraryMusic,
            "Nada sonando",
            "Conecta tu USB y elige una carpeta o un álbum.",
        ) {
            Button(onClick = onBrowse, modifier = Modifier.height(56.dp)) {
                Text("Explorar música", fontSize = 18.sp)
            }
        }
        return
    }

    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize()) {
        // Fondo: la portada en muy baja resolución estirada = efecto desenfocado
        if (cover != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(cover).size(24).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.6f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.82f)))
            )
        )

        Row(
            Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                Crossfade(targetState = showQueue, modifier = Modifier.fillMaxSize(), label = "queue") { queue ->
                    if (queue) {
                        QueuePanel(vm, pb, Modifier.fillMaxSize())
                    } else {
                        FileImage(
                            cover,
                            Modifier.fillMaxSize()
                                .shadow(28.dp, RoundedCornerShape(22.dp))
                                .clip(RoundedCornerShape(22.dp)),
                            spinning = pb.isPlaying,
                        )
                    }
                }
            }

            Spacer(Modifier.width(40.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    pb.album.uppercase(), color = accent, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    pb.title, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    pb.artist.ifEmpty { " " }, fontSize = 22.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(28.dp))
                SeekBar(pb, onSeek = vm::seek)
                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BigIconButton(
                        Icons.Rounded.Shuffle, vm::toggleShuffle, size = 56.dp,
                        tint = if (pb.shuffle) accent else Color.White.copy(alpha = 0.45f),
                    )
                    BigIconButton(Icons.Rounded.SkipPrevious, vm::previous, size = 76.dp, iconScale = 0.62f)
                    BigIconButton(
                        if (pb.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        vm::togglePlay, size = 100.dp, iconScale = 0.56f,
                        tint = MaterialTheme.colorScheme.onPrimary, background = accent,
                    )
                    BigIconButton(Icons.Rounded.SkipNext, vm::next, size = 76.dp, iconScale = 0.62f)
                    BigIconButton(
                        if (pb.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        vm::cycleRepeat, size = 56.dp,
                        tint = if (pb.repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.45f),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Pista ${pb.index + 1} de ${pb.count}",
                        fontSize = 15.sp, color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f),
                    )
                    BigIconButton(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        vm::toggleFavoriteCurrent, size = 52.dp,
                        tint = if (favorite) accent else Color.White.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(6.dp))
                    BigIconButton(
                        Icons.AutoMirrored.Rounded.PlaylistAdd, vm::requestAddCurrentToList, size = 52.dp,
                        tint = Color.White.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(6.dp))
                    BigIconButton(
                        Icons.AutoMirrored.Rounded.QueueMusic, { showQueue = !showQueue }, size = 52.dp,
                        tint = if (showQueue) accent else Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeekBar(pb: PlaybackUi, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = pb.durationMs
    val fraction = dragging ?: if (dur > 0) (pb.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val accent = MaterialTheme.colorScheme.primary
    Slider(
        value = fraction,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            val d = dragging
            if (d != null && dur > 0) onSeek((d * dur).toLong())
            dragging = null
        },
        enabled = dur > 0,
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = Color.White.copy(alpha = 0.18f),
        ),
    )
    Row(Modifier.fillMaxWidth()) {
        val shown = if (dragging != null) (fraction * dur).toLong() else pb.positionMs
        Text(formatTime(shown), fontSize = 15.sp, color = Color.White.copy(alpha = 0.65f))
        Spacer(Modifier.weight(1f))
        Text(if (dur > 0) formatTime(dur) else "--:--", fontSize = 15.sp, color = Color.White.copy(alpha = 0.65f))
    }
}

@Composable
private fun QueuePanel(vm: MainViewModel, pb: PlaybackUi, modifier: Modifier) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (pb.index - 2).coerceAtLeast(0))
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.55f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Cola de reproducción", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::requestSaveQueue) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text("Guardar como lista", fontSize = 15.sp)
            }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            items(count = pb.count, key = { it }) { i ->
                val item = remember(pb.queueVersion, i) { vm.queueItem(i) }
                val md = item?.mediaMetadata
                val title = (md?.title ?: md?.displayTitle)?.toString()
                    ?: item?.mediaId?.let { File(it).nameWithoutExtension } ?: ""
                val current = i == pb.index
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable { vm.playIndex(i) }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(36.dp)) {
                        if (current) {
                            Icon(Icons.Rounded.Equalizer, null, tint = accent, modifier = Modifier.size(22.dp))
                        } else {
                            Text("${i + 1}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.45f))
                        }
                    }
                    Text(
                        title, fontSize = 17.sp,
                        color = if (current) accent else Color.White,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(vm: MainViewModel, pb: PlaybackUi, onOpen: () -> Unit) {
    val cover by vm.cover.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().background(Color(0xFF1B1C22))) {
        LinearProgressIndicator(
            progress = { if (pb.durationMs > 0) (pb.positionMs.toFloat() / pb.durationMs).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = accent,
            trackColor = Color.White.copy(alpha = 0.1f),
        )
        Row(
            Modifier.fillMaxWidth().height(84.dp).clickable(onClick = onOpen).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileImage(cover, Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(pb.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pb.artist, fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            BigIconButton(Icons.Rounded.SkipPrevious, vm::previous, size = 60.dp)
            Spacer(Modifier.width(8.dp))
            BigIconButton(
                if (pb.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                vm::togglePlay, size = 64.dp,
                tint = MaterialTheme.colorScheme.onPrimary, background = accent,
            )
            Spacer(Modifier.width(8.dp))
            BigIconButton(Icons.Rounded.SkipNext, vm::next, size = 60.dp)
        }
    }
}
