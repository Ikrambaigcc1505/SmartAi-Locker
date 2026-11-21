package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.VaultItemEntity
import com.example.viewmodel.PreviewPayload
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@Composable
fun VaultMediaViewerDialog(
    payload: PreviewPayload,
    backgroundAudioPlay: Boolean,
    onPlaybackActiveChanged: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    if (payload is PreviewPayload.None) return

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val title = when (payload) {
                is PreviewPayload.Loading -> payload.item.originalName
                is PreviewPayload.ImageBytes -> payload.item.originalName
                is PreviewPayload.VideoFile -> payload.item.originalName
                is PreviewPayload.AudioFile -> payload.item.originalName
                is PreviewPayload.PdfFile -> payload.item.originalName
                is PreviewPayload.TextContent -> payload.item.originalName
                is PreviewPayload.ArchiveOrBinary -> payload.item.originalName
                is PreviewPayload.Error -> payload.item.originalName
                PreviewPayload.None -> ""
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Top Viewer Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted viewer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("viewer_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close viewer"
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when (payload) {
                        is PreviewPayload.Loading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Decrypting in sandbox...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        is PreviewPayload.ImageBytes -> {
                            ZoomableImageViewer(bytes = payload.bytes)
                        }

                        is PreviewPayload.VideoFile -> {
                            InAppVideoPlayer(
                                file = payload.tempFile,
                                onPlaybackActiveChanged = onPlaybackActiveChanged
                            )
                        }

                        is PreviewPayload.AudioFile -> {
                            InAppAudioPlayer(
                                item = payload.item,
                                file = payload.tempFile,
                                backgroundAudioPlay = backgroundAudioPlay,
                                onPlaybackActiveChanged = onPlaybackActiveChanged
                            )
                        }

                        is PreviewPayload.PdfFile -> {
                            InAppPdfViewer(file = payload.tempFile)
                        }

                        is PreviewPayload.TextContent -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = payload.text,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        is PreviewPayload.ArchiveOrBinary -> {
                            ArchiveSummaryViewer(
                                item = payload.item,
                                entries = payload.entries,
                                summary = payload.infoSummary
                            )
                        }

                        is PreviewPayload.Error -> {
                            Text(
                                text = payload.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(24.dp)
                            )
                        }

                        PreviewPayload.None -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImageViewer(bytes: ByteArray) {
    val bitmap = remember(bytes) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    if (bitmap == null) {
        Text("Unable to decode image.")
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 4.5f)
                    scale = nextScale
                    if (nextScale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Encrypted vault photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

@Composable
private fun InAppVideoPlayer(
    file: File,
    onPlaybackActiveChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPlaybackActiveChanged(playing)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            onPlaybackActiveChanged(false)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(400L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                }
            )
            IconButton(
                onClick = { isFullscreen = !isFullscreen },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Toggle fullscreen",
                    tint = Color.White
                )
            }
        }

        if (!isFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Slider(
                    value = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                    onValueChange = { fraction ->
                        if (durationMs > 0) exoPlayer.seekTo((fraction * durationMs).toLong())
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMillis(positionMs), style = MaterialTheme.typography.labelSmall)
                    Text(formatMillis(durationMs), style = MaterialTheme.typography.labelSmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                    }) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledIconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(onClick = {
                        exoPlayer.seekTo(exoPlayer.currentPosition + 10_000L)
                    }) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s")
                    }
                }
            }
        }
    }
}

@Composable
private fun InAppAudioPlayer(
    item: VaultItemEntity,
    file: File,
    backgroundAudioPlay: Boolean,
    onPlaybackActiveChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPlaybackActiveChanged(playing)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            if (!backgroundAudioPlay) {
                onPlaybackActiveChanged(false)
                exoPlayer.release()
            } else {
                exoPlayer.release()
                onPlaybackActiveChanged(false)
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(350L)
        }
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        highlightBorder = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = "Audio vault player",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = item.originalName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatFileSize(item.sizeBytes)} • Sandboxed Audio",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Slider(
                value = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                onValueChange = { fraction ->
                    if (durationMs > 0) exoPlayer.seekTo((fraction * durationMs).toLong())
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMillis(positionMs), style = MaterialTheme.typography.labelSmall)
                Text(formatMillis(durationMs), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                }) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s")
                }
                FilledIconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause audio" else "Play audio",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = {
                    exoPlayer.seekTo(exoPlayer.currentPosition + 10_000L)
                }) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10s")
                }
            }
        }
    }
}

@Composable
private fun InAppPdfViewer(file: File) {
    var pageIndex by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val rendererHolder = remember(file) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            pageCount = renderer.pageCount
            pfd to renderer
        }.getOrNull()
    }

    DisposableEffect(rendererHolder) {
        onDispose {
            runCatching {
                rendererHolder?.second?.close()
                rendererHolder?.first?.close()
            }
        }
    }

    LaunchedEffect(rendererHolder, pageIndex) {
        val renderer = rendererHolder?.second ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect
        val safeIdx = pageIndex.coerceIn(0, pageCount - 1)
        val page = renderer.openPage(safeIdx)
        val bmp = Bitmap.createBitmap(
            (page.width * 2).coerceAtMost(1600),
            (page.height * 2).coerceAtMost(2400),
            Bitmap.Config.ARGB_8888
        )
        bmp.eraseColor(android.graphics.Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderedBitmap = bmp
    }

    if (rendererHolder == null || pageCount == 0) {
        Text("Unable to render PDF document.")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            renderedBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )
            } ?: CircularProgressIndicator()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (pageIndex > 0) pageIndex-- },
                enabled = pageIndex > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous page")
            }
            Text(
                text = "Page ${pageIndex + 1} of $pageCount",
                style = MaterialTheme.typography.labelLarge
            )
            IconButton(
                onClick = { if (pageIndex < pageCount - 1) pageIndex++ },
                enabled = pageIndex < pageCount - 1
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next page")
            }
        }
    }
}

@Composable
private fun ArchiveSummaryViewer(
    item: VaultItemEntity,
    entries: List<String>,
    summary: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), highlightBorder = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(item.originalName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatFileSize(item.sizeBytes)} • ${item.aiCategoryTag}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        }
        if (entries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text("Contained Entries", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries) { entry ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatMillis(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
