@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.visionit.vcut.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.visionit.vcut.model.*
import com.visionit.vcut.ui.theme.*
import com.visionit.vcut.viewmodel.EditorViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onPickExtraClip: () -> Unit,
    onPickMusic: () -> Unit,
    onPickImageOverlay: () -> Unit,
    onRequestRecordVoice: () -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.project.collectAsState()
    val thumbnails by viewModel.thumbnails.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isRecording by viewModel.isRecordingVoice.collectAsState()
    val recordingSec by viewModel.recordingSeconds.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()

    var showExportSheet by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var showToolPanel by remember { mutableStateOf(true) }

    val selectedClip = project.clips.getOrNull(project.selectedClipIndex) ?: project.clips.firstOrNull()

    // ExoPlayer for smooth local video preview
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
        }
    }

    LaunchedEffect(selectedClip?.uriString) {
        selectedClip?.let { clip ->
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(clip.uriString)))
            exoPlayer.prepare()
            exoPlayer.seekTo(clip.trimStartMs)
        }
    }

    LaunchedEffect(selectedClip?.speed, project.audio.originalMuted, project.audio.originalVolume) {
        selectedClip?.let { clip ->
            exoPlayer.playbackParameters = PlaybackParameters(clip.speed)
        }
        exoPlayer.volume = if (project.audio.originalMuted) 0f else project.audio.originalVolume
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(80)
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMsg) {
        statusMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = PureWhite
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(IceBlueTint, PureWhite, PureWhite)
                    )
                )
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. TOP BAR (Back, 9:16 Pill, Undo/Redo, Export 4K)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = PureWhite,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .size(40.dp)
                            .border(1.dp, SubtleBorder, CircleShape)
                            .clickable(onClick = onBack)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = CharcoalText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = PureWhite,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .border(1.dp, SubtleBorder, RoundedCornerShape(50))
                            .clickable { viewModel.cycleAspectRatio() }
                    ) {
                        Text(
                            text = project.aspectRatio.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CharcoalText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) CharcoalText else SlateSubtext.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) CharcoalText else SlateSubtext.copy(alpha = 0.4f)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(listOf(SkyBluePrimary, Color(0xFF38BDF8)))
                            )
                            .shadow(6.dp, RoundedCornerShape(50), ambientColor = SkyBluePrimary)
                            .clickable {
                                exoPlayer.pause()
                                isPlaying = false
                                showExportSheet = true
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Export 4K",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                }
            }

            // 2. VIDEO PREVIEW CARD (Rounded, Frosted Floating Controls, Live Text Preview)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(210.dp)
                    .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = SkyBluePrimary.copy(alpha = 0.15f))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF0F172A))
            ) {
                if (selectedClip != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Live Text Overlays Preview
                project.textOverlays.forEach { item ->
                    if (currentPosMs in item.startMs..item.endMs || !isPlaying) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 26.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (item.hasBackgroundPill) Color.Black.copy(alpha = 0.65f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = item.text,
                                color = Color(item.colorHex),
                                fontSize = (item.fontSizeSp * 0.65f).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Frosted Floating Playback Controls (Rewind / Play-Pause / Forward)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .background(PureWhite.copy(alpha = 0.25f))
                        .border(1.dp, PureWhite.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            val t = (exoPlayer.currentPosition - 3000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(t)
                            currentPosMs = t
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.FastRewind, "Rewind", tint = PureWhite)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PureWhite.copy(alpha = 0.35f))
                            .clickable {
                                if (isPlaying) {
                                    exoPlayer.pause()
                                    isPlaying = false
                                } else {
                                    exoPlayer.play()
                                    isPlaying = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = PureWhite,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val t = (exoPlayer.currentPosition + 3000L).coerceAtMost(project.totalDurationMs)
                            exoPlayer.seekTo(t)
                            currentPosMs = t
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.FastForward, "Forward", tint = PureWhite)
                    }
                }
            }

            // Timecode row beneath preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatTime(currentPosMs)} / ${formatTime(project.totalDurationMs)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CharcoalText
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (project.audio.enhanceVoiceEnabled) {
                        SmallStatusBadge("Voice+", SkyBlueDeep)
                    }
                    if (project.audio.noiseReductionEnabled) {
                        SmallStatusBadge("NR", SuccessGreen)
                    }
                    if (isRecording) {
                        SmallStatusBadge("REC ${formatSec(recordingSec)}", DangerRed)
                    }
                }
            }

            // 3. CLEAN WHITE MULTI-TRACK TIMELINE CARD
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = PureWhite,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, Color(0xFFD6EAFF), RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Quick actions bar (Play, Split, Delete, +)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(Modifier.width(72.dp))
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Timeline Play",
                            tint = SkyBluePrimary,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    if (isPlaying) {
                                        exoPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        exoPlayer.play()
                                        isPlaying = true
                                    }
                                }
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TimelineMiniAction(Icons.Rounded.ContentCut, "Split") {
                                viewModel.splitAtPosition(currentPosMs)
                            }
                            TimelineMiniAction(Icons.Outlined.Delete, "Delete") {
                                viewModel.deleteSelectedClip()
                            }
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add Clip",
                                tint = CharcoalText,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(onClick = onPickExtraClip)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Interactive Multi-Track Timeline with Diamond Sky-Blue Playhead
                    val totalDur = project.totalDurationMs.coerceAtLeast(1000L)
                    val progressFraction = (currentPosMs.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(144.dp)
                            .pointerInput(totalDur) {
                                detectHorizontalDragGestures { change, _ ->
                                    change.consume()
                                    val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val seekMs = (frac * totalDur).toLong()
                                    currentPosMs = seekMs
                                    exoPlayer.seekTo(seekMs)
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            // Time Ruler
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { f ->
                                    Text(
                                        text = formatTime((totalDur * f).toLong()),
                                        fontSize = 10.sp,
                                        color = SlateSubtext
                                    )
                                }
                            }

                            // Track 1: Video Strip with Thumbnails
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE0F2FE))
                                    .border(2.dp, MintClipBorder, RoundedCornerShape(10.dp))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (thumbnails.isNotEmpty()) {
                                    thumbnails.forEach { bmp ->
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    }
                                } else {
                                    project.clips.forEachIndexed { idx, clip ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (idx == project.selectedClipIndex) SkyBluePrimary.copy(alpha = 0.25f)
                                                    else SoftSkySurface
                                                )
                                                .clickable { viewModel.selectClip(idx) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = clip.displayName,
                                                fontSize = 10.sp,
                                                color = CharcoalText,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // Track 2: Text Track (Pastel Lavender-Blue)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(PastelLavenderTrack)
                                    .border(1.dp, Color(0xFFC7D2FE), RoundedCornerShape(9.dp))
                                    .clickable {
                                        viewModel.selectTab(EditorTab.TEXT)
                                        showToolPanel = true
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = project.textOverlays.firstOrNull()?.text ?: "Text Track  (+ Tap to add)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CharcoalText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Track 3: Audio Waveform Track (Ocean Blue)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFFE0F4FF))
                                    .border(1.dp, SkyBluePrimary.copy(alpha = 0.6f), RoundedCornerShape(9.dp))
                                    .clickable {
                                        viewModel.selectTab(EditorTab.AUDIO)
                                        showToolPanel = true
                                    }
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 5.dp)) {
                                    val barCount = 46
                                    val step = size.width / barCount
                                    val midY = size.height / 2f
                                    for (i in 0 until barCount) {
                                        val hFactor = ((i * 37 + 13) % 10) / 10f * 0.85f + 0.15f
                                        val halfH = midY * hFactor
                                        val x = i * step + step / 2f
                                        drawLine(
                                            color = OceanWaveBlue,
                                            start = Offset(x, midY - halfH),
                                            end = Offset(x, midY + halfH),
                                            strokeWidth = 3f
                                        )
                                    }
                                }
                                val audioLabel = when {
                                    project.audio.voiceoverFilePath != null -> "Voiceover Active"
                                    project.audio.externalAudioName != null -> project.audio.externalAudioName!!
                                    else -> "Original Audio"
                                }
                                Text(
                                    text = audioLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SkyBlueDeep,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(end = 8.dp, top = 2.dp)
                                )
                            }
                        }

                        // Vertical Sky-Blue Playhead with Diamond Handle
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val x = size.width * progressFraction
                            val diamondTop = 14.dp.toPx()
                            val r = 6.dp.toPx()
                            val path = Path().apply {
                                moveTo(x, diamondTop - r)
                                lineTo(x + r, diamondTop)
                                lineTo(x, diamondTop + r)
                                lineTo(x - r, diamondTop)
                                close()
                            }
                            drawPath(path, color = SkyBluePrimary)
                            drawLine(
                                color = SkyBluePrimary,
                                start = Offset(x, diamondTop + r),
                                end = Offset(x, size.height),
                                strokeWidth = 2.2.dp.toPx()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 4. ACTIVE TOOL CONTROL PANEL (Scrollable middle area above bottom dock)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (showToolPanel) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = IceBlueTint.copy(alpha = 0.65f),
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, SubtleBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            when (activeTab) {
                                EditorTab.EDIT -> EditToolPanel(
                                    clip = selectedClip,
                                    onSpeedChange = { viewModel.updateSelectedClipSpeed(it) },
                                    onRotate = { viewModel.rotateSelectedClip() },
                                    onFlip = { viewModel.flipSelectedClip() },
                                    onTrimChange = { s, e -> viewModel.updateSelectedClipTrim(s, e) }
                                )
                                EditorTab.AUDIO -> AudioToolPanel(
                                    audio = project.audio,
                                    isRecording = isRecording,
                                    recordingSec = recordingSec,
                                    isProcessing = isProcessing,
                                    onRecordToggle = onRequestRecordVoice,
                                    onExtractAudio = { viewModel.extractAudioFromCurrentClip() },
                                    onEnhanceNow = { viewModel.enhanceRecordedVoiceNow() },
                                    onPickMusic = onPickMusic,
                                    onUpdateAudio = { viewModel.updateAudio(it) }
                                )
                                EditorTab.TEXT -> TextToolPanel(
                                    overlays = project.textOverlays,
                                    onAddText = { txt, col, sz, pill ->
                                        viewModel.addTextOverlay(txt, col, sz, pill)
                                    },
                                    onRemoveText = { viewModel.removeTextOverlay(it) }
                                )
                                EditorTab.OVERLAY -> OverlayToolPanel(
                                    overlay = project.imageOverlay,
                                    onPickImage = onPickImageOverlay,
                                    onUpdateOverlay = { viewModel.updateImageOverlay(it) },
                                    onRemoveOverlay = { viewModel.removeImageOverlay() }
                                )
                                EditorTab.EFFECTS -> EffectsToolPanel(
                                    current = project.color.effect,
                                    onSelect = { eff -> viewModel.updateColor { it.copy(effect = eff) } }
                                )
                                EditorTab.FILTERS -> FiltersToolPanel(
                                    color = project.color,
                                    onUpdate = { viewModel.updateColor(it) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 5. BOTTOM FROSTED WHITE DOCK BAR (Exact 6 icons: Edit, Audio, Text, Overlay, Effects, Filters)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = PureWhite,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .border(1.dp, Color(0xFFE5EFFA), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomDockTab(Icons.Rounded.ContentCut, "Edit", activeTab == EditorTab.EDIT) {
                        viewModel.selectTab(EditorTab.EDIT); showToolPanel = true
                    }
                    BottomDockTab(Icons.Rounded.MusicNote, "Audio", activeTab == EditorTab.AUDIO) {
                        viewModel.selectTab(EditorTab.AUDIO); showToolPanel = true
                    }
                    BottomDockTab(Icons.Rounded.Title, "Text", activeTab == EditorTab.TEXT) {
                        viewModel.selectTab(EditorTab.TEXT); showToolPanel = true
                    }
                    BottomDockTab(Icons.Outlined.Layers, "Overlay", activeTab == EditorTab.OVERLAY) {
                        viewModel.selectTab(EditorTab.OVERLAY); showToolPanel = true
                    }
                    BottomDockTab(Icons.Outlined.AutoFixHigh, "Effects", activeTab == EditorTab.EFFECTS) {
                        viewModel.selectTab(EditorTab.EFFECTS); showToolPanel = true
                    }
                    BottomDockTab(Icons.Outlined.FilterVintage, "Filters", activeTab == EditorTab.FILTERS) {
                        viewModel.selectTab(EditorTab.FILTERS); showToolPanel = true
                    }
                }
            }
        }
    }

    if (showExportSheet) {
        ExportBottomSheet(
            isExporting = isProcessing,
            progress = exportProgress,
            onDismiss = { showExportSheet = false },
            onStartExport = { res, fps ->
                viewModel.exportVideo(res, fps) { ok, _ ->
                    if (ok) showExportSheet = false
                }
            }
        )
    }
}

@Composable
private fun AudioToolPanel(
    audio: AudioSettings,
    isRecording: Boolean,
    recordingSec: Int,
    isProcessing: Boolean,
    onRecordToggle: () -> Unit,
    onExtractAudio: () -> Unit,
    onEnhanceNow: () -> Unit,
    onPickMusic: () -> Unit,
    onUpdateAudio: ((AudioSettings) -> AudioSettings) -> Unit
) {
    Text("Voice Studio & Audio", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CharcoalText)
    Spacer(Modifier.height(8.dp))

    // Row 1: Voice Record + Extract Audio + Add Music
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onRecordToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) DangerRed else SkyBluePrimary
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (isRecording) "Stop ${formatSec(recordingSec)}" else "Record",
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        OutlinedButton(
            onClick = onExtractAudio,
            enabled = !isProcessing,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.AudioFile, null, modifier = Modifier.size(16.dp), tint = SkyBlueDeep)
            Spacer(Modifier.width(4.dp))
            Text("Extract", fontSize = 11.sp, color = CharcoalText, maxLines = 1)
        }

        OutlinedButton(
            onClick = onPickMusic,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.LibraryMusic, null, modifier = Modifier.size(16.dp), tint = SkyBlueDeep)
            Spacer(Modifier.width(4.dp))
            Text("Music", fontSize = 11.sp, color = CharcoalText, maxLines = 1)
        }
    }

    Spacer(Modifier.height(10.dp))

    // Row 2: Enhance Voice + Noise Remove toggles
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = audio.enhanceVoiceEnabled,
            onClick = { onUpdateAudio { it.copy(enhanceVoiceEnabled = !it.enhanceVoiceEnabled) } },
            label = { Text("Enhance Voice", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = audio.noiseReductionEnabled,
            onClick = { onUpdateAudio { it.copy(noiseReductionEnabled = !it.noiseReductionEnabled) } },
            label = { Text("Noise Remove", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Rounded.CleaningServices, null, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.weight(1f)
        )
    }

    if (audio.voiceoverFilePath != null) {
        Spacer(Modifier.height(6.dp))
        FilledTonalButton(
            onClick = onEnhanceNow,
            enabled = !isProcessing,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {
            Text(
                text = if (isProcessing) "Processing Voice..." else "Apply Studio Enhance + Noise Remove Now",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    if (audio.noiseReductionEnabled) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Noise Remove Strength: ${(audio.noiseReductionStrength * 100).toInt()}%",
            fontSize = 11.sp,
            color = SlateSubtext
        )
        Slider(
            value = audio.noiseReductionStrength,
            onValueChange = { v -> onUpdateAudio { it.copy(noiseReductionStrength = v) } }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Original Volume: ${(audio.originalVolume * 100).toInt()}%", fontSize = 11.sp, color = SlateSubtext)
        TextButton(onClick = { onUpdateAudio { it.copy(originalMuted = !it.originalMuted) } }) {
            Text(if (audio.originalMuted) "Unmute" else "Mute", fontSize = 11.sp)
        }
    }
    Slider(
        value = audio.originalVolume,
        onValueChange = { v -> onUpdateAudio { it.copy(originalVolume = v, originalMuted = false) } }
    )
}

@Composable
private fun EditToolPanel(
    clip: VideoClipItem?,
    onSpeedChange: (Float) -> Unit,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onTrimChange: (Long, Long) -> Unit
) {
    if (clip == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Speed: ${String.format(Locale.US, "%.2fx", clip.speed)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onRotate, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Rounded.RotateRight, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${clip.rotationDegrees}°", fontSize = 11.sp)
            }
            FilledTonalButton(onClick = onFlip, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Rounded.Flip, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Flip", fontSize = 11.sp)
            }
        }
    }
    Slider(
        value = clip.speed,
        onValueChange = onSpeedChange,
        valueRange = 0.25f..3.0f
    )
    Text(
        text = "Trim: ${formatTime(clip.trimStartMs)} → ${formatTime(clip.trimEndMs)}",
        fontSize = 11.sp,
        color = SlateSubtext
    )
    val durF = clip.originalDurationMs.toFloat().coerceAtLeast(1000f)
    RangeSlider(
        value = clip.trimStartMs.toFloat()..clip.trimEndMs.toFloat(),
        onValueChange = { range -> onTrimChange(range.start.toLong(), range.endInclusive.toLong()) },
        valueRange = 0f..durF
    )
}

@Composable
private fun TextToolPanel(
    overlays: List<TextOverlayItem>,
    onAddText: (String, Long, Float, Boolean) -> Unit,
    onRemoveText: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var selectedColor by remember { mutableLongStateOf(0xFFFFFFFF) }
    var hasPill by remember { mutableStateOf(true) }
    val colors = listOf(0xFFFFFFFF, 0xFF38BDF8, 0xFFFACC15, 0xFFFB7185, 0xFF34D399)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            placeholder = { Text("বাংলা বা English টেক্সট লিখুন...", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                onAddText(textInput, selectedColor, 26f, hasPill)
                textInput = ""
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Add", fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { hex ->
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(hex))
                    .border(
                        width = if (selectedColor == hex) 2.dp else 1.dp,
                        color = if (selectedColor == hex) SkyBlueDeep else SubtleBorder,
                        shape = CircleShape
                    )
                    .clickable { selectedColor = hex }
            )
        }
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = hasPill,
            onClick = { hasPill = !hasPill },
            label = { Text("BG Box", fontSize = 11.sp) }
        )
    }
    if (overlays.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            overlays.forEach { ov ->
                InputChip(
                    enabled = true,
                    selected = false,
                    onClick = { onRemoveText(ov.id) },
                    label = { Text(ov.text, fontSize = 11.sp, maxLines = 1) },
                    trailingIcon = { Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp)) }
                )
            }
        }
    }
}

@Composable
private fun OverlayToolPanel(
    overlay: ImageOverlayItem?,
    onPickImage: () -> Unit,
    onUpdateOverlay: ((ImageOverlayItem) -> ImageOverlayItem) -> Unit,
    onRemoveOverlay: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Logo / Image Overlay (PIP)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onPickImage, shape = RoundedCornerShape(10.dp)) {
                Text(if (overlay == null) "Add Image" else "Change", fontSize = 11.sp)
            }
            if (overlay != null) {
                OutlinedButton(onClick = onRemoveOverlay, shape = RoundedCornerShape(10.dp)) {
                    Text("Remove", fontSize = 11.sp, color = DangerRed)
                }
            }
        }
    }
    if (overlay != null) {
        Spacer(Modifier.height(6.dp))
        Text("Size: ${(overlay.scaleFraction * 100).toInt()}%", fontSize = 11.sp, color = SlateSubtext)
        Slider(
            value = overlay.scaleFraction,
            onValueChange = { s -> onUpdateOverlay { it.copy(scaleFraction = s) } },
            valueRange = 0.12f..0.60f
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Top-L", "Top-R", "Bot-L", "Bot-R").forEachIndexed { idx, label ->
                FilterChip(
                    selected = overlay.cornerPosition == idx,
                    onClick = { onUpdateOverlay { it.copy(cornerPosition = idx) } },
                    label = { Text(label, fontSize = 10.sp) }
                )
            }
        }
    }
}

@Composable
private fun EffectsToolPanel(
    current: VisualEffect,
    onSelect: (VisualEffect) -> Unit
) {
    Text("Visual Effects", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CharcoalText)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VisualEffect.entries.forEach { eff ->
            FilterChip(
                selected = current == eff,
                onClick = { onSelect(eff) },
                label = { Text(eff.label, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun FiltersToolPanel(
    color: ColorFilterSettings,
    onUpdate: ((ColorFilterSettings) -> ColorFilterSettings) -> Unit
) {
    Text("Brightness: ${String.format(Locale.US, "%.2f", color.brightness)}", fontSize = 11.sp, color = SlateSubtext)
    Slider(
        value = color.brightness,
        onValueChange = { v -> onUpdate { it.copy(brightness = v) } },
        valueRange = -0.4f..0.4f
    )
    Text("Contrast: ${String.format(Locale.US, "%.2f", color.contrast)}", fontSize = 11.sp, color = SlateSubtext)
    Slider(
        value = color.contrast,
        onValueChange = { v -> onUpdate { it.copy(contrast = v) } },
        valueRange = 0.5f..1.8f
    )
    Text("Saturation: ${String.format(Locale.US, "%.2f", color.saturation)}", fontSize = 11.sp, color = SlateSubtext)
    Slider(
        value = color.saturation,
        onValueChange = { v -> onUpdate { it.copy(saturation = v) } },
        valueRange = 0.0f..2.0f
    )
}

@Composable
private fun BottomDockTab(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) SkyBluePrimary else CharcoalText,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) SkyBluePrimary else CharcoalText
        )
    }
}

@Composable
private fun TimelineMiniAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = CharcoalText, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 9.sp, color = SlateSubtext)
    }
}

@Composable
private fun SmallStatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

private fun formatSec(sec: Int): String {
    return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
}
