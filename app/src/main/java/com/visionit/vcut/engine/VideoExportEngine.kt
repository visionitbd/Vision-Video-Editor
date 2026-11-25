package com.visionit.vcut.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.visionit.vcut.model.AspectRatioOption
import com.visionit.vcut.model.AudioSettings
import com.visionit.vcut.model.EditorProjectState
import com.visionit.vcut.model.ExportResolution
import com.visionit.vcut.model.TextOverlayItem
import com.visionit.vcut.model.VisualEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.resume

data class ExportResult(
    val success: Boolean,
    val message: String,
    val outputPathOrUri: String? = null
)

class VideoExportEngine(private val context: Context) {

    /**
     * Extracts audio track from a video URI as clean 192kbps M4A and saves a copy to Music/VisionCut.
     */
    suspend fun extractAudioFromVideo(
        videoUri: Uri,
        applyNoiseRemove: Boolean,
        applyEnhanceVoice: Boolean
    ): ExportResult = withContext(Dispatchers.IO) {
        val inputFile = copyUriToCache(videoUri, "extract_src_${System.currentTimeMillis()}.mp4")
            ?: return@withContext ExportResult(false, "ভিডিও ফাইল পড়া যায়নি।")

        val outDir = File(context.cacheDir, "extracted_audio").apply { mkdirs() }
        val outFile = File(outDir, "extracted_${System.currentTimeMillis()}.m4a")

        val audioFilter = buildAudioFilterChain(
            noiseReduction = applyNoiseRemove,
            noiseStrength = 0.7f,
            enhanceVoice = applyEnhanceVoice,
            volume = 1.0f,
            fadeInSec = 0f,
            fadeOutSec = 0f,
            durationSec = 0.0
        )

        val afFlag = if (audioFilter.isNotBlank()) "-af "$audioFilter"" else ""
        val cmd = "-y -i "${inputFile.absolutePath}" -vn $afFlag -c:a aac -b:a 192k "${outFile.absolutePath}""

        val success = runFFmpegCommand(cmd, totalDurationMs = 1L) {}
        inputFile.delete()

        if (success && outFile.exists() && outFile.length() > 0L) {
            saveAudioToMediaStore(outFile, "VisionCut_Audio_${System.currentTimeMillis()}.m4a")
            ExportResult(
                success = true,
                message = "অডিও এক্সট্র্যাক্ট হয়ে Music/VisionCut ফোল্ডারে সেভ হয়েছে!",
                outputPathOrUri = outFile.absolutePath
            )
        } else {
            ExportResult(false, "অডিও এক্সট্র্যাক্ট করতে সমস্যা হয়েছে।")
        }
    }

    /**
     * Applies Enhance Voice + Noise Removal directly to a recorded voiceover file so user can preview it cleanly.
     */
    suspend fun processVoiceoverAudio(
        inputAudioPath: String,
        noiseRemove: Boolean,
        noiseStrength: Float,
        enhanceVoice: Boolean
    ): ExportResult = withContext(Dispatchers.IO) {
        val src = File(inputAudioPath)
        if (!src.exists()) return@withContext ExportResult(false, "ভয়েস ফাইল পাওয়া যায়নি।")

        val outFile = File(src.parentFile, "enhanced_${System.currentTimeMillis()}.m4a")
        val chain = buildAudioFilterChain(
            noiseReduction = noiseRemove,
            noiseStrength = noiseStrength,
            enhanceVoice = enhanceVoice,
            volume = 1.0f,
            fadeInSec = 0f,
            fadeOutSec = 0f,
            durationSec = 0.0
        )
        if (chain.isBlank()) {
            return@withContext ExportResult(true, "কোনো অডিও ফিল্টার চালু নেই।", inputAudioPath)
        }

        val cmd = "-y -i "${src.absolutePath}" -af "$chain" -c:a aac -b:a 192k "${outFile.absolutePath}""
        val ok = runFFmpegCommand(cmd, 1L) {}
        if (ok && outFile.exists() && outFile.length() > 0L) {
            ExportResult(true, "ভয়েস এনহ্যান্স ও নয়েজ রিমুভ সম্পন্ন!", outFile.absolutePath)
        } else {
            ExportResult(false, "ভয়েস প্রসেসিং ব্যর্থ হয়েছে।")
        }
    }

    /**
     * Full watermark-free video export with trim, split/merge, speed, rotate/flip, aspect ratio,
     * color grading, visual effects, Bengali/English text overlays, image PIP, music/voiceover mixing,
     * Enhance Voice, and Noise Reduction.
     */
    suspend fun exportProject(
        state: EditorProjectState,
        resolution: ExportResolution,
        fps: Int,
        onProgress: (Float) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        if (state.clips.isEmpty()) {
            return@withContext ExportResult(false, "এক্সপোর্ট করার জন্য কোনো ভিডিও ক্লিপ নেই।")
        }

        val workDir = File(context.cacheDir, "export_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val (targetW, targetH) = computeDimensions(state.aspectRatio, resolution)
            val totalDurationMs = state.totalDurationMs
            val totalDurationSec = totalDurationMs / 1000.0

            // 1. Copy input video clips to local cache
            val localClips = state.clips.mapIndexed { idx, clip ->
                val f = copyUriToCache(Uri.parse(clip.uriString), "clip_${idx}.mp4", workDir)
                    ?: return@withContext ExportResult(false, "ক্লিপ #${idx + 1} লোড করা যায়নি।")
                clip to f
            }

            // 2. Render text overlays to transparent PNGs (guarantees flawless Bengali & Unicode rendering!)
            val textPngFiles = state.textOverlays.mapIndexed { idx, item ->
                val png = File(workDir, "text_overlay_$idx.png")
                renderTextOverlayPng(item, targetW, targetH, png)
                item to png
            }

            // 3. Copy optional image overlay (PIP)
            val imageOverlayFile = state.imageOverlay?.let {
                copyUriToCache(Uri.parse(it.uriString), "pip_overlay.png", workDir)
            }

            // 4. Copy optional external music track
            val musicFile = state.audio.externalAudioUri?.let {
                copyUriToCache(Uri.parse(it), "bgm_audio.m4a", workDir)
            }

            val voiceoverFile = state.audio.voiceoverFilePath?.let { File(it) }?.takeIf { it.exists() }

            // Build FFmpeg inputs
            val inputs = StringBuilder()
            var nextInputIdx = 0

            val clipInputIndices = localClips.map { (_, file) ->
                inputs.append("-i "${file.absolutePath}" ")
                nextInputIdx++
            }
            val textInputIndices = textPngFiles.map { (_, png) ->
                inputs.append("-i "${png.absolutePath}" ")
                nextInputIdx++
            }
            val pipInputIdx = if (imageOverlayFile != null) {
                inputs.append("-i "${imageOverlayFile.absolutePath}" ")
                nextInputIdx++
            } else -1

            val musicInputIdx = if (musicFile != null) {
                inputs.append("-i "${musicFile.absolutePath}" ")
                nextInputIdx++
            } else -1

            val voiceInputIdx = if (voiceoverFile != null) {
                inputs.append("-i "${voiceoverFile.absolutePath}" ")
                nextInputIdx++
            } else -1

            // Build filter_complex
            val fc = StringBuilder()
            val vConcatLabels = mutableListOf<String>()
            val aConcatLabels = mutableListOf<String>()

            localClips.forEachIndexed { idx, (clip, _) ->
                val inIdx = clipInputIndices[idx]
                val startSec = String.format(Locale.US, "%.3f", clip.trimStartMs / 1000.0)
                val endSec = String.format(Locale.US, "%.3f", clip.trimEndMs / 1000.0)
                val ptsFactor = String.format(Locale.US, "%.4f", 1.0f / clip.speed)

                val vFilters = mutableListOf(
                    "trim=start=$startSec:end=$endSec",
                    "setpts=($ptsFactor)*(PTS-STARTPTS)"
                )
                when (clip.rotationDegrees % 360) {
                    90 -> vFilters.add("transpose=1")
                    180 -> vFilters.add("transpose=1,transpose=1")
                    270 -> vFilters.add("transpose=2")
                }
                if (clip.flipHorizontal) vFilters.add("hflip")
                vFilters.add("scale=$targetW:$targetH:force_original_aspect_ratio=decrease")
                vFilters.add("pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2:black")
                vFilters.add("fps=$fps,setsar=1")

                fc.append("[$inIdx:v]${vFilters.joinToString(",")}[v$idx];")
                vConcatLabels.add("[v$idx]")

                val atempoChain = buildAtempoChain(clip.speed)
                fc.append("[$inIdx:a]atrim=start=$startSec:end=$endSec,asetpts=PTS-STARTPTS,$atempoChain[a$idx];")
                aConcatLabels.add("[a$idx]")
            }

            // Concat clips
            var currentV = if (localClips.size == 1) {
                "[v0]"
            } else {
                fc.append("${vConcatLabels.joinToString("")}concat=n=${localClips.size}:v=1:a=0[vconcat];")
                "[vconcat]"
            }

            var currentA = if (localClips.size == 1) {
                "[a0]"
            } else {
                fc.append("${aConcatLabels.joinToString("")}concat=n=${localClips.size}:v=0:a=1[aconcat];")
                "[aconcat]"
            }

            // Color & Visual Effects
            val colorFilters = mutableListOf<String>()
            val c = state.color
            if (c.brightness != 0f || c.contrast != 1f || c.saturation != 1f) {
                colorFilters.add(
                    String.format(
                        Locale.US,
                        "eq=brightness=%.2f:contrast=%.2f:saturation=%.2f",
                        c.brightness, c.contrast, c.saturation
                    )
                )
            }
            when (c.effect) {
                VisualEffect.VIGNETTE -> colorFilters.add("vignette=PI/4")
                VisualEffect.SHARPEN -> colorFilters.add("unsharp=5:5:1.0:5:5:0.0")
                VisualEffect.BLACK_WHITE -> colorFilters.add("hue=s=0")
                VisualEffect.VINTAGE -> colorFilters.add("colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131")
                VisualEffect.SOFT_BLUR -> colorFilters.add("boxblur=2:1")
                VisualEffect.NONE -> Unit
            }
            if (colorFilters.isNotEmpty()) {
                fc.append("$currentV${colorFilters.joinToString(",")}[vcolor];")
                currentV = "[vcolor]"
            }

            // Apply Image PIP Overlay
            if (pipInputIdx >= 0 && state.imageOverlay != null) {
                val pip = state.imageOverlay
                val pipW = (targetW * pip.scaleFraction).toInt().coerceAtLeast(64)
                val posExpr = when (pip.cornerPosition) {
                    0 -> "40:40"
                    1 -> "W-w-40:40"
                    2 -> "40:H-h-40"
                    else -> "W-w-40:H-h-40"
                }
                fc.append("[$pipInputIdx:v]scale=$pipW:-1[pipscaled];")
                fc.append("${currentV}[pipscaled]overlay=$posExpr[vpip];")
                currentV = "[vpip]"
            }

            // Apply Bengali/English Text Overlays (PNGs with enable between start & end)
            textPngFiles.forEachIndexed { idx, (item, _) ->
                val tIdx = textInputIndices[idx]
                val s = String.format(Locale.US, "%.2f", item.startMs / 1000.0)
                val e = String.format(Locale.US, "%.2f", item.endMs / 1000.0)
                val outLabel = "[vtxt$idx]"
                fc.append("${currentV}[$tIdx:v]overlay=0:0:enable='between(t,$s,$e)'$outLabel;")
                currentV = outLabel
            }

            // Audio processing: original + Enhance Voice + Noise Remove + Voiceover + BGM
            val audioSettings = state.audio
            val mixStreams = mutableListOf<String>()

            if (!audioSettings.originalMuted && audioSettings.originalVolume > 0.01f) {
                val origChain = buildAudioFilterChain(
                    noiseReduction = audioSettings.noiseReductionEnabled,
                    noiseStrength = audioSettings.noiseReductionStrength,
                    enhanceVoice = audioSettings.enhanceVoiceEnabled,
                    volume = audioSettings.originalVolume,
                    fadeInSec = audioSettings.fadeInSec,
                    fadeOutSec = audioSettings.fadeOutSec,
                    durationSec = totalDurationSec
                )
                fc.append("${currentA}$origChain[aorig];")
                mixStreams.add("[aorig]")
            }

            if (voiceInputIdx >= 0) {
                val voChain = buildAudioFilterChain(
                    noiseReduction = audioSettings.noiseReductionEnabled,
                    noiseStrength = audioSettings.noiseReductionStrength,
                    enhanceVoice = audioSettings.enhanceVoiceEnabled,
                    volume = audioSettings.voiceoverVolume,
                    fadeInSec = 0f,
                    fadeOutSec = 0f,
                    durationSec = totalDurationSec
                )
                fc.append("[$voiceInputIdx:a]$voChain[avo];")
                mixStreams.add("[avo]")
            }

            if (musicInputIdx >= 0) {
                val bgmVol = String.format(Locale.US, "%.2f", audioSettings.externalVolume)
                fc.append("[$musicInputIdx:a]volume=$bgmVol[abgm];")
                mixStreams.add("[abgm]")
            }

            val finalAudioMap = when (mixStreams.size) {
                0 -> ""
                1 -> "-map "${mixStreams.first()}" -c:a aac -b:a 192k"
                else -> {
                    fc.append("${mixStreams.joinToString("")}amix=inputs=${mixStreams.size}:duration=first:dropout_transition=2[afinal];")
                    "-map "[afinal]" -c:a aac -b:a 192k"
                }
            }

            val filterGraph = fc.toString().trimEnd(';')
            val outputFile = File(workDir, "VisionCut_Export.mp4")

            val cmd = "$inputs -filter_complex "$filterGraph" -map "$currentV" $finalAudioMap " +
                "-c:v libx264 -preset ultrafast -crf 21 -pix_fmt yuv420p -movflags +faststart " +
                "-y "${outputFile.absolutePath}""

            val ok = runFFmpegCommand(cmd, totalDurationMs, onProgress)
            if (ok && outputFile.exists() && outputFile.length() > 0L) {
                val savedUri = saveVideoToGallery(outputFile, "VisionCut_${System.currentTimeMillis()}.mp4")
                ExportResult(
                    success = true,
                    message = "ভিডিও গ্যালারিতে (Movies/VisionCut) সেভ হয়েছে!",
                    outputPathOrUri = savedUri?.toString() ?: outputFile.absolutePath
                )
            } else {
                ExportResult(false, "ভিডিও এক্সপোর্ট ব্যর্থ হয়েছে। আবার চেষ্টা করুন।")
            }
        } catch (e: Exception) {
            ExportResult(false, "ত্রুটি: ${e.localizedMessage ?: "Unknown error"}")
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun buildAudioFilterChain(
        noiseReduction: Boolean,
        noiseStrength: Float,
        enhanceVoice: Boolean,
        volume: Float,
        fadeInSec: Float,
        fadeOutSec: Float,
        durationSec: Double
    ): String {
        val parts = mutableListOf<String>()
        if (noiseReduction) {
            val nfDb = (-18 - (noiseStrength.coerceIn(0f, 1f) * 22)).toInt()
            parts.add("highpass=f=85")
            parts.add("afftdn=nf=$nfDb")
        }
        if (enhanceVoice) {
            parts.add("lowpass=f=12500")
            parts.add("acompressor=threshold=-18dB:ratio=3:attack=10:release=120")
            parts.add("equalizer=f=220:t=q:w=1.2:g=2")
            parts.add("equalizer=f=3200:t=q:w=1.5:g=3")
            parts.add("loudnorm=I=-16:TP=-1.5:LRA=11")
        }
        if (fadeInSec > 0.05f) {
            parts.add(String.format(Locale.US, "afade=t=in:st=0:d=%.2f", fadeInSec))
        }
        if (fadeOutSec > 0.05f && durationSec > fadeOutSec) {
            val st = (durationSec - fadeOutSec).coerceAtLeast(0.0)
            parts.add(String.format(Locale.US, "afade=t=out:st=%.2f:d=%.2f", st, fadeOutSec))
        }
        parts.add(String.format(Locale.US, "volume=%.2f", volume))
        return parts.joinToString(",")
    }

    private fun buildAtempoChain(speed: Float): String {
        val s = speed.coerceIn(0.25f, 4.0f)
        return when {
            s < 0.5f -> String.format(Locale.US, "atempo=0.5,atempo=%.3f", s / 0.5f)
            s > 2.0f -> String.format(Locale.US, "atempo=2.0,atempo=%.3f", s / 2.0f)
            else -> String.format(Locale.US, "atempo=%.3f", s)
        }
    }

    private fun computeDimensions(ratio: AspectRatioOption, res: ExportResolution): Pair<Int, Int> {
        val shortSide = res.shortSide
        val (w, h) = when (ratio) {
            AspectRatioOption.PORTRAIT_9_16 -> shortSide to (shortSide * 16 / 9)
            AspectRatioOption.LANDSCAPE_16_9 -> (shortSide * 16 / 9) to shortSide
            AspectRatioOption.SQUARE_1_1 -> shortSide to shortSide
            AspectRatioOption.SOCIAL_4_5 -> shortSide to (shortSide * 5 / 4)
        }
        return (w - (w % 2)) to (h - (h % 2))
    }

    private fun renderTextOverlayPng(item: TextOverlayItem, width: Int, height: Int, outFile: File) {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val scale = width / 360f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.colorHex.toInt()
            textSize = item.fontSizeSp * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(6f * scale, 0f, 2f * scale, Color.argb(140, 0, 0, 0))
        }

        val cx = width / 2f
        val cy = height * item.verticalPositionFraction
        val textWidth = textPaint.measureText(item.text)
        val fm = textPaint.fontMetrics

        if (item.hasBackgroundPill) {
            val padH = 22f * scale
            val padV = 12f * scale
            val rect = RectF(
                cx - textWidth / 2f - padH,
                cy + fm.ascent - padV,
                cx + textWidth / 2f + padH,
                cy + fm.descent + padV
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(175, 17, 24, 39)
            }
            canvas.drawRoundRect(rect, 16f * scale, 16f * scale, bgPaint)
        }

        canvas.drawText(item.text, cx, cy, textPaint)
        FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private suspend fun runFFmpegCommand(
        cmd: String,
        totalDurationMs: Long,
        onProgress: (Float) -> Unit
    ): Boolean = suspendCancellableCoroutine { cont ->
        val session = FFmpegKit.executeAsync(
            cmd,
            { completedSession ->
                if (cont.isActive) {
                    val ok = ReturnCode.isSuccess(completedSession.returnCode)
                    if (ok) onProgress(1f)
                    cont.resume(ok)
                }
            },
            {},
            { stats ->
                if (totalDurationMs > 0 && stats.time > 0) {
                    val p = (stats.time.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 0.99f)
                    onProgress(p)
                }
            }
        )
        cont.invokeOnCancellation { session.cancel() }
    }

    private fun copyUriToCache(uri: Uri, name: String, dir: File = context.cacheDir): File? {
        return try {
            val out = File(dir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    private fun saveVideoToGallery(file: File, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VisionCut")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun saveAudioToMediaStore(file: File, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/VisionCut")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}
