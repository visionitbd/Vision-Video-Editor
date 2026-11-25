package com.visionit.vcut.model

import java.util.UUID

enum class AspectRatioOption(val label: String, val ratioW: Int, val ratioH: Int) {
    PORTRAIT_9_16("9:16", 9, 16),
    LANDSCAPE_16_9("16:9", 16, 9),
    SQUARE_1_1("1:1", 1, 1),
    SOCIAL_4_5("4:5", 4, 5)
}

enum class EditorTab(val label: String) {
    EDIT("Edit"),
    AUDIO("Audio"),
    TEXT("Text"),
    OVERLAY("Overlay"),
    EFFECTS("Effects"),
    FILTERS("Filters")
}

enum class VisualEffect(val label: String) {
    NONE("None"),
    VIGNETTE("Vignette"),
    SHARPEN("HD Crisp"),
    BLACK_WHITE("B&W"),
    VINTAGE("Vintage"),
    SOFT_BLUR("Soft Glow")
}

enum class ExportResolution(val label: String, val shortSide: Int) {
    HD_720("720p", 720),
    FHD_1080("1080p", 1080),
    QHD_2K("2K", 1440),
    UHD_4K("4K", 2160)
}

data class VideoClipItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val displayName: String,
    val originalDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = originalDurationMs,
    val speed: Float = 1.0f,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false
) {
    val effectiveDurationMs: Long
        get() = ((trimEndMs - trimStartMs).coerceAtLeast(200L) / speed).toLong()
}

data class TextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long = 0L,
    val endMs: Long = 5000L,
    val fontSizeSp: Float = 26f,
    val colorHex: Long = 0xFFFFFFFF,
    val hasBackgroundPill: Boolean = true,
    val verticalPositionFraction: Float = 0.78f
)

data class ImageOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val scaleFraction: Float = 0.28f,
    val cornerPosition: Int = 1 // 0=TopLeft, 1=TopRight, 2=BottomLeft, 3=BottomRight
)

data class AudioSettings(
    val originalVolume: Float = 1.0f,
    val originalMuted: Boolean = false,
    val externalAudioUri: String? = null,
    val externalAudioName: String? = null,
    val externalVolume: Float = 0.85f,
    val voiceoverFilePath: String? = null,
    val voiceoverVolume: Float = 1.0f,
    val extractedAudioPath: String? = null,
    val fadeInSec: Float = 0.0f,
    val fadeOutSec: Float = 0.0f,
    val noiseReductionEnabled: Boolean = false,
    val noiseReductionStrength: Float = 0.65f, // 0.0..1.0 -> maps to afftdn nf -18..-40
    val enhanceVoiceEnabled: Boolean = false
)

data class ColorFilterSettings(
    val brightness: Float = 0.0f, // -0.5..0.5
    val contrast: Float = 1.0f,   // 0.5..1.8
    val saturation: Float = 1.0f, // 0.0..2.0
    val effect: VisualEffect = VisualEffect.NONE
)

data class EditorProjectState(
    val projectId: String = UUID.randomUUID().toString(),
    val title: String = "Project_01",
    val createdAtLabel: String = "",
    val clips: List<VideoClipItem> = emptyList(),
    val selectedClipIndex: Int = 0,
    val aspectRatio: AspectRatioOption = AspectRatioOption.PORTRAIT_9_16,
    val textOverlays: List<TextOverlayItem> = emptyList(),
    val imageOverlay: ImageOverlayItem? = null,
    val audio: AudioSettings = AudioSettings(),
    val color: ColorFilterSettings = ColorFilterSettings()
) {
    val totalDurationMs: Long
        get() = clips.sumOf { it.effectiveDurationMs }.coerceAtLeast(1000L)
}

data class SavedDraftSummary(
    val projectId: String,
    val title: String,
    val dateLabel: String,
    val thumbnailUri: String,
    val durationMs: Long,
    val stateJson: String
)
