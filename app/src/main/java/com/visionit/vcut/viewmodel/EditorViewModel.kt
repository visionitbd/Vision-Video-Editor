package com.visionit.vcut.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.visionit.vcut.audio.VoiceRecorderManager
import com.visionit.vcut.data.ProjectRepository
import com.visionit.vcut.engine.VideoExportEngine
import com.visionit.vcut.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ProjectRepository(application)
    private val exportEngine = VideoExportEngine(application)
    private val voiceRecorder = VoiceRecorderManager(application)

    private val _project = MutableStateFlow(EditorProjectState())
    val project: StateFlow<EditorProjectState> = _project.asStateFlow()

    private val _drafts = MutableStateFlow<List<SavedDraftSummary>>(emptyList())
    val drafts: StateFlow<List<SavedDraftSummary>> = _drafts.asStateFlow()

    private val _thumbnails = MutableStateFlow<List<Bitmap>>(emptyList())
    val thumbnails: StateFlow<List<Bitmap>> = _thumbnails.asStateFlow()

    private val _activeTab = MutableStateFlow(EditorTab.EDIT)
    val activeTab: StateFlow<EditorTab> = _activeTab.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Voice Recording state
    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()
    private var recordingTimerJob: Job? = null

    // Export / Processing state
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    // Undo / Redo Stacks
    private val undoStack = ArrayDeque<EditorProjectState>()
    private val redoStack = ArrayDeque<EditorProjectState>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    init {
        refreshDrafts()
    }

    fun refreshDrafts() {
        _drafts.value = repo.loadDrafts()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun selectTab(tab: EditorTab) {
        _activeTab.value = tab
    }

    private fun updateProjectWithUndo(transform: (EditorProjectState) -> EditorProjectState) {
        undoStack.addLast(_project.value)
        if (undoStack.size > 25) undoStack.removeFirst()
        redoStack.clear()
        _project.value = transform(_project.value)
        syncUndoFlags()
        repo.saveProject(_project.value)
        refreshDrafts()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_project.value)
        _project.value = undoStack.removeLast()
        syncUndoFlags()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_project.value)
        _project.value = redoStack.removeLast()
        syncUndoFlags()
    }

    private fun syncUndoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun startNewProject(videoUri: Uri, initialTab: EditorTab = EditorTab.EDIT) {
        viewModelScope.launch {
            val durationMs = queryVideoDurationMs(videoUri)
            val clip = VideoClipItem(
                uriString = videoUri.toString(),
                displayName = "Clip 1",
                originalDurationMs = durationMs,
                trimStartMs = 0L,
                trimEndMs = durationMs
            )
            undoStack.clear()
            redoStack.clear()
            syncUndoFlags()
            val newState = EditorProjectState(
                title = "Edit_${System.currentTimeMillis() % 1000}",
                clips = listOf(clip),
                selectedClipIndex = 0
            )
            _project.value = newState
            _activeTab.value = initialTab
            repo.saveProject(newState)
            refreshDrafts()
            extractThumbnails(videoUri)
        }
    }

    fun openDraft(draft: SavedDraftSummary) {
        val parsed = repo.parseState(draft.stateJson) ?: return
        undoStack.clear()
        redoStack.clear()
        syncUndoFlags()
        _project.value = parsed
        parsed.clips.firstOrNull()?.let { clip ->
            viewModelScope.launch { extractThumbnails(Uri.parse(clip.uriString)) }
        }
    }

    fun deleteDraft(projectId: String) {
        repo.deleteDraft(projectId)
        refreshDrafts()
    }

    fun addClip(videoUri: Uri) {
        viewModelScope.launch {
            val durationMs = queryVideoDurationMs(videoUri)
            val newClip = VideoClipItem(
                uriString = videoUri.toString(),
                displayName = "Clip ${_project.value.clips.size + 1}",
                originalDurationMs = durationMs,
                trimStartMs = 0L,
                trimEndMs = durationMs
            )
            updateProjectWithUndo { st ->
                st.copy(
                    clips = st.clips + newClip,
                    selectedClipIndex = st.clips.size
                )
            }
            _statusMessage.value = "নতুন ভিডিও ক্লিপ যোগ করা হয়েছে।"
        }
    }

    fun selectClip(index: Int) {
        if (index in _project.value.clips.indices) {
            _project.value = _project.value.copy(selectedClipIndex = index)
        }
    }

    fun splitAtPosition(playheadMs: Long) {
        val state = _project.value
        val idx = state.selectedClipIndex.coerceIn(0, (state.clips.size - 1).coerceAtLeast(0))
        val clip = state.clips.getOrNull(idx) ?: return
        val splitPoint = (clip.trimStartMs + (playheadMs * clip.speed).toLong())
            .coerceIn(clip.trimStartMs + 300L, clip.trimEndMs - 300L)
        if (splitPoint <= clip.trimStartMs + 250L || splitPoint >= clip.trimEndMs - 250L) {
            _statusMessage.value = "স্প্লিট করার জন্য প্লে-হেড ক্লিপের মাঝামাঝি নিন।"
            return
        }
        val firstHalf = clip.copy(trimEndMs = splitPoint)
        val secondHalf = clip.copy(
            id = java.util.UUID.randomUUID().toString(),
            displayName = "${clip.displayName} (B)",
            trimStartMs = splitPoint
        )
        val updated = state.clips.toMutableList().apply {
            removeAt(idx)
            add(idx, secondHalf)
            add(idx, firstHalf)
        }
        updateProjectWithUndo { it.copy(clips = updated, selectedClipIndex = idx + 1) }
        _statusMessage.value = "ক্লিপ স্প্লিট করা হয়েছে!"
    }

    fun deleteSelectedClip() {
        val state = _project.value
        if (state.clips.size <= 1) {
            _statusMessage.value = "কমপক্ষে একটি ক্লিপ থাকতে হবে।"
            return
        }
        val idx = state.selectedClipIndex.coerceIn(0, state.clips.lastIndex)
        val updated = state.clips.toMutableList().apply { removeAt(idx) }
        updateProjectWithUndo {
            it.copy(clips = updated, selectedClipIndex = (idx - 1).coerceAtLeast(0))
        }
        _statusMessage.value = "ক্লিপ ডিলিট করা হয়েছে।"
    }

    fun cycleAspectRatio() {
        val all = AspectRatioOption.entries
        val next = all[(all.indexOf(_project.value.aspectRatio) + 1) % all.size]
        updateProjectWithUndo { it.copy(aspectRatio = next) }
    }

    fun updateSelectedClipTrim(startMs: Long, endMs: Long) {
        updateSelectedClip { clip ->
            clip.copy(
                trimStartMs = startMs.coerceIn(0L, clip.originalDurationMs - 300L),
                trimEndMs = endMs.coerceIn(startMs + 300L, clip.originalDurationMs)
            )
        }
    }

    fun updateSelectedClipSpeed(speed: Float) {
        updateSelectedClip { it.copy(speed = speed.coerceIn(0.25f, 4.0f)) }
    }

    fun rotateSelectedClip() {
        updateSelectedClip { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    }

    fun flipSelectedClip() {
        updateSelectedClip { it.copy(flipHorizontal = !it.flipHorizontal) }
    }

    private fun updateSelectedClip(mapper: (VideoClipItem) -> VideoClipItem) {
        val state = _project.value
        val idx = state.selectedClipIndex
        if (idx !in state.clips.indices) return
        val updated = state.clips.toMutableList().apply { this[idx] = mapper(this[idx]) }
        updateProjectWithUndo { it.copy(clips = updated) }
    }

    // --- Audio, Voice Record, Enhance Voice, Extract Audio, Noise Remove ---

    fun updateAudio(mapper: (AudioSettings) -> AudioSettings) {
        updateProjectWithUndo { it.copy(audio = mapper(it.audio)) }
    }

    fun attachMusic(uri: Uri, name: String) {
        updateAudio { it.copy(externalAudioUri = uri.toString(), externalAudioName = name) }
        _statusMessage.value = "ব্যাকগ্রাউন্ড মিউজিক যোগ হয়েছে।"
    }

    fun toggleVoiceRecording() {
        if (_isRecordingVoice.value) {
            recordingTimerJob?.cancel()
            val file = voiceRecorder.stopRecording()
            _isRecordingVoice.value = false
            _recordingSeconds.value = 0
            if (file != null) {
                updateAudio { it.copy(voiceoverFilePath = file.absolutePath) }
                _statusMessage.value = "ভয়েস রেকর্ড সম্পন্ন ও টাইমলাইনে যুক্ত হয়েছে!"
            } else {
                _statusMessage.value = "ভয়েস রেকর্ড সেভ করা যায়নি।"
            }
        } else {
            try {
                voiceRecorder.startRecording()
                _isRecordingVoice.value = true
                _recordingSeconds.value = 0
                recordingTimerJob = viewModelScope.launch {
                    while (_isRecordingVoice.value) {
                        delay(1000)
                        _recordingSeconds.value += 1
                    }
                }
            } catch (e: Exception) {
                _isRecordingVoice.value = false
                _statusMessage.value = "মাইক্রোফোন চালু করা যায়নি: ${e.localizedMessage}"
            }
        }
    }

    fun extractAudioFromCurrentClip() {
        val clip = _project.value.clips.getOrNull(_project.value.selectedClipIndex)
            ?: _project.value.clips.firstOrNull()
            ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            val audio = _project.value.audio
            val res = exportEngine.extractAudioFromVideo(
                videoUri = Uri.parse(clip.uriString),
                applyNoiseRemove = audio.noiseReductionEnabled,
                applyEnhanceVoice = audio.enhanceVoiceEnabled
            )
            _isProcessing.value = false
            if (res.success && res.outputPathOrUri != null) {
                updateAudio {
                    it.copy(
                        extractedAudioPath = res.outputPathOrUri,
                        voiceoverFilePath = res.outputPathOrUri,
                        originalMuted = true
                    )
                }
            }
            _statusMessage.value = res.message
        }
    }

    fun enhanceRecordedVoiceNow() {
        val path = _project.value.audio.voiceoverFilePath
        if (path.isNullOrBlank()) {
            _statusMessage.value = "আগে ভয়েস রেকর্ড করুন বা ভিডিও থেকে অডিও এক্সট্র্যাক্ট করুন।"
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            val res = exportEngine.processVoiceoverAudio(
                inputAudioPath = path,
                noiseRemove = true,
                noiseStrength = _project.value.audio.noiseReductionStrength,
                enhanceVoice = true
            )
            _isProcessing.value = false
            if (res.success && res.outputPathOrUri != null) {
                updateAudio {
                    it.copy(
                        voiceoverFilePath = res.outputPathOrUri,
                        enhanceVoiceEnabled = true,
                        noiseReductionEnabled = true
                    )
                }
            }
            _statusMessage.value = res.message
        }
    }

    // --- Text, Image Overlay & Color Filters ---

    fun addTextOverlay(text: String, colorHex: Long, fontSize: Float, hasPill: Boolean) {
        if (text.isBlank()) return
        val dur = _project.value.totalDurationMs
        val item = TextOverlayItem(
            text = text.trim(),
            startMs = 0L,
            endMs = dur.coerceAtMost(6000L),
            fontSizeSp = fontSize,
            colorHex = colorHex,
            hasBackgroundPill = hasPill
        )
        updateProjectWithUndo { it.copy(textOverlays = it.textOverlays + item) }
        _statusMessage.value = "টেক্সট ওভারলে যোগ হয়েছে।"
    }

    fun removeTextOverlay(id: String) {
        updateProjectWithUndo { st -> st.copy(textOverlays = st.textOverlays.filterNot { it.id == id }) }
    }

    fun setImageOverlay(uri: Uri) {
        updateProjectWithUndo {
            it.copy(imageOverlay = ImageOverlayItem(uriString = uri.toString()))
        }
        _statusMessage.value = "ছবি/লোগো ওভারলে (PIP) যোগ হয়েছে।"
    }

    fun updateImageOverlay(mapper: (ImageOverlayItem) -> ImageOverlayItem) {
        val current = _project.value.imageOverlay ?: return
        updateProjectWithUndo { it.copy(imageOverlay = mapper(current)) }
    }

    fun removeImageOverlay() {
        updateProjectWithUndo { it.copy(imageOverlay = null) }
    }

    fun updateColor(mapper: (ColorFilterSettings) -> ColorFilterSettings) {
        updateProjectWithUndo { it.copy(color = mapper(it.color)) }
    }

    // --- Export ---

    fun exportVideo(resolution: ExportResolution, fps: Int, onFinished: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            _exportProgress.value = 0f
            val res = exportEngine.exportProject(_project.value, resolution, fps) { p ->
                _exportProgress.value = p
            }
            _isProcessing.value = false
            _statusMessage.value = res.message
            onFinished(res.success, res.message)
        }
    }

    private suspend fun queryVideoDurationMs(uri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(getApplication(), uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?.coerceAtLeast(1000L) ?: 10000L
        } catch (_: Exception) {
            10000L
        } finally {
            retriever.release()
        }
    }

    private suspend fun extractThumbnails(uri: Uri) = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val list = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(getApplication(), uri)
            val durUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 5000L) * 1000L
            val count = 8
            for (i in 0 until count) {
                val timeUs = (durUs * i) / count
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, 120, 120, true)
                    list.add(scaled)
                    if (scaled != frame) frame.recycle()
                }
            }
        } catch (_: Exception) {
            // Fallback handled gracefully by UI
        } finally {
            retriever.release()
        }
        _thumbnails.value = list
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorder.release()
    }
}
