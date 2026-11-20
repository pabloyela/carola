package com.carola.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.carola.player.data.CoverStatus
import com.carola.player.data.formatTime
import com.carola.player.player.Eq
import java.io.File

@Composable
fun NowPlayingScreen(vm: MainViewModel, onBrowse: () -> Unit) {
    val pb by vm.playback.collectAsStateWithLifecycle()
    val cover by vm.cover.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val big by vm.bigPlayer.collectAsStateWithLifecycle()
    val coverStatus by vm.coverStatus.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf(Panel.COVER) }

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
    BoxWithConstraints(Modifier.fillMaxSize()) {
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

        // Pantallas bajas (teléfono en horizontal, algunos radios): todo más compacto para que quepa
        val compact = maxHeight < 480.dp
        val playSize = if (compact) 80.dp else 100.dp
        val skipSize = if (compact) 62.dp else 76.dp
        val sideSize = if (compact) 48.dp else 56.dp

        if (big) {
            BigPlayer(vm, pb, cover, favorite, compact)
        } else Row(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 24.dp else 36.dp, vertical = if (compact) 14.dp else 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                Crossfade(targetState = panel, modifier = Modifier.fillMaxSize(), label = "panel") { p ->
                    when (p) {
                        Panel.QUEUE -> QueuePanel(vm, pb, Modifier.fillMaxSize())
                        Panel.EQ -> EqPanel(vm, Modifier.fillMaxSize())
                        Panel.COVER -> Box(Modifier.fillMaxSize()) {
                            FileImage(
                                cover,
                                Modifier.fillMaxSize()
                                    .then(rememberPlayerGestures(vm) {})
                                    .shadow(28.dp, RoundedCornerShape(22.dp))
                                    .clip(RoundedCornerShape(22.dp)),
                                spinning = pb.isPlaying,
                            )
                            if (cover == null) CoverBadge(coverStatus, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }

            Spacer(Modifier.width(if (compact) 24.dp else 40.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                // Fila superior: álbum + favorito, agregar a lista y cola
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            pb.album.uppercase(), color = accent, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Pista ${pb.index + 1} de ${pb.count}",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                    BigIconButton(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        vm::toggleFavoriteCurrent, size = 48.dp,
                        tint = if (favorite) accent else Color.White.copy(alpha = 0.7f),
                    )
                    BigIconButton(
                        Icons.AutoMirrored.Rounded.PlaylistAdd, vm::requestAddCurrentToList, size = 48.dp,
                        tint = Color.White.copy(alpha = 0.7f),
                    )
                    BigIconButton(
                        Icons.Rounded.Tune, { panel = if (panel == Panel.EQ) Panel.COVER else Panel.EQ }, size = 48.dp,
                        tint = if (panel == Panel.EQ) accent else Color.White.copy(alpha = 0.7f),
                    )
                    BigIconButton(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        { panel = if (panel == Panel.QUEUE) Panel.COVER else Panel.QUEUE }, size = 48.dp,
                        tint = if (panel == Panel.QUEUE) accent else Color.White.copy(alpha = 0.7f),
                    )
                    BigIconButton(
                        Icons.Rounded.Fullscreen, { vm.setBigPlayer(true) }, size = 48.dp,
                        tint = Color.White.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
                Text(
                    pb.title,
                    fontSize = if (compact) 26.sp else 34.sp,
                    lineHeight = if (compact) 31.sp else 40.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
                Text(
                    pb.artist.ifEmpty { " " }, fontSize = if (compact) 19.sp else 22.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(if (compact) 8.dp else 24.dp))
                SeekBar(pb, onSeek = vm::seek)
                Spacer(Modifier.height(if (compact) 4.dp else 14.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BigIconButton(
                        Icons.Rounded.Shuffle, vm::toggleShuffle, size = sideSize,
                        tint = if (pb.shuffle) accent else Color.White.copy(alpha = 0.45f),
                    )
                    BigIconButton(Icons.Rounded.SkipPrevious, vm::previous, size = skipSize, iconScale = 0.62f)
                    BigIconButton(
                        if (pb.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        vm::togglePlay, size = playSize, iconScale = 0.56f,
                        tint = MaterialTheme.colorScheme.onPrimary, background = accent,
                    )
                    BigIconButton(Icons.Rounded.SkipNext, vm::next, size = skipSize, iconScale = 0.62f)
                    BigIconButton(
                        if (pb.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        vm::cycleRepeat, size = sideSize,
                        tint = if (pb.repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

private enum class Panel { COVER, QUEUE, EQ }

/** Etiqueta sobre el tocadiscos: buscando portada, no encontrada, sin internet... */
@Composable
private fun CoverBadge(status: CoverStatus?, modifier: Modifier = Modifier) {
    if (status == null) return
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier.padding(bottom = 14.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == CoverStatus.SEARCHING) {
            CircularProgressIndicator(Modifier.size(16.dp), color = accent, strokeWidth = 2.dp)
        } else {
            Icon(
                when (status) {
                    CoverStatus.OFFLINE -> Icons.Rounded.CloudOff
                    CoverStatus.DISABLED -> Icons.Rounded.CloudOff
                    else -> Icons.Rounded.ImageNotSupported
                },
                null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(status.label, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
    }
}

/**
 * Gestos sobre la portada:
 *  - deslizar a la izquierda = siguiente, a la derecha = anterior (la portada acompaña el dedo)
 *  - doble toque = pausa / reproducir
 *  - un toque = [onTap]
 */
@Composable
private fun rememberPlayerGestures(vm: MainViewModel, onTap: () -> Unit): Modifier {
    val tap by rememberUpdatedState(onTap)
    var drag by remember { mutableFloatStateOf(0f) }
    val shown by animateFloatAsState(drag, label = "swipe")
    val threshold = with(LocalDensity.current) { 90.dp.toPx() }
    return Modifier
        .graphicsLayer {
            translationX = shown * 0.6f
            alpha = 1f - (abs(shown) / (threshold * 4f)).coerceAtMost(0.4f)
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    when {
                        drag <= -threshold -> vm.next()
                        drag >= threshold -> vm.previous()
                    }
                    drag = 0f
                },
                onDragCancel = { drag = 0f },
            ) { change, dx ->
                change.consume()
                drag += dx
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { tap() },
                onDoubleTap = { vm.togglePlay() },
            )
        }
}

/** Vista grande: sin menú lateral, portada enorme y botones gigantes. Toca la portada para más opciones. */
@Composable
private fun BigPlayer(vm: MainViewModel, pb: PlaybackUi, cover: File?, favorite: Boolean, compact: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    var extras by remember { mutableStateOf(false) }
    BackHandler { vm.setBigPlayer(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = if (compact) 12.dp else 24.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                    FileImage(
                        cover,
                        Modifier.fillMaxSize()
                            .then(rememberPlayerGestures(vm) { extras = !extras })
                            .shadow(30.dp, RoundedCornerShape(26.dp))
                            .clip(RoundedCornerShape(26.dp)),
                        spinning = pb.isPlaying,
                    )
                    if (cover == null) {
                        val st by vm.coverStatus.collectAsStateWithLifecycle()
                        CoverBadge(st, Modifier.align(Alignment.BottomCenter))
                    }
                }
                Spacer(Modifier.width(if (compact) 22.dp else 40.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pb.title,
                        fontSize = if (compact) 30.sp else 46.sp,
                        lineHeight = if (compact) 35.sp else 52.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        pb.artist.ifEmpty { " " }, fontSize = if (compact) 21.sp else 28.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (!compact) {
                        Text(
                            pb.album, fontSize = 18.sp, color = accent,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
                    SeekBar(pb, onSeek = vm::seek)
                    if (extras) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BigIconButton(
                                Icons.Rounded.Shuffle, vm::toggleShuffle, size = 52.dp,
                                tint = if (pb.shuffle) accent else Color.White.copy(alpha = 0.5f),
                            )
                            BigIconButton(
                                if (pb.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                vm::cycleRepeat, size = 52.dp,
                                tint = if (pb.repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.5f),
                            )
                            BigIconButton(
                                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                vm::toggleFavoriteCurrent, size = 52.dp,
                                tint = if (favorite) accent else Color.White.copy(alpha = 0.7f),
                            )
                            BigIconButton(
                                Icons.AutoMirrored.Rounded.PlaylistAdd, vm::requestAddCurrentToList, size = 52.dp,
                                tint = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 6.dp else 16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BigIconButton(Icons.Rounded.SkipPrevious, vm::previous, size = if (compact) 80.dp else 108.dp, iconScale = 0.64f)
                BigIconButton(
                    if (pb.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    vm::togglePlay, size = if (compact) 96.dp else 136.dp, iconScale = 0.58f,
                    tint = MaterialTheme.colorScheme.onPrimary, background = accent,
                )
                BigIconButton(Icons.Rounded.SkipNext, vm::next, size = if (compact) 80.dp else 108.dp, iconScale = 0.64f)
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            BigIconButton(
                Icons.Rounded.FullscreenExit, { vm.setBigPlayer(false) }, size = 52.dp,
                tint = Color.White.copy(alpha = 0.75f), background = Color.Black.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun EqPanel(vm: MainViewModel, modifier: Modifier) {
    val eq by vm.eq.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.6f))
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ecualizador", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(eq.preset, fontSize = 15.sp, color = accent, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Eq.PRESETS.forEach { (name, _) ->
                val selected = eq.preset == name
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (selected) accent else Color.White.copy(alpha = 0.1f))
                        .clickable { vm.setEqPreset(name) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        name, fontSize = 15.sp,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Eq.BANDS.forEachIndexed { i, band ->
            val g = eq.gains.getOrElse(i) { 0f }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(band.label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(62.dp))
                Slider(
                    value = g,
                    onValueChange = { vm.setEqBand(i, it) },
                    valueRange = -Eq.MAX_DB..Eq.MAX_DB,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "%+.1f".format(g), fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.width(44.dp).padding(start = 6.dp),
                )
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
