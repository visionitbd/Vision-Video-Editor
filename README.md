# Vision Cut (V-Cut) — Version 1 MVP Android Video Editor

Clean White & Sky-Blue offline Android video editor built with **Kotlin**, **Jetpack Compose**, **Media3 ExoPlayer**, and **FFmpegKit**.

## Features (v1.0 MVP)
- **Clean White & Sky-Blue Home Screen**: Ice-blue hero header, `New Video` & `Quick Trim` cards, Recent Projects strip with live thumbnails, and 3x3 minimal tool grid.
- **Pro White Glass Editor Screen**:
  - Multi-clip timeline with real extracted filmstrip thumbnails
  - Interactive sky-blue playhead with diamond handle synced to ExoPlayer
  - **Edit**: Trim, Split at playhead, Delete, Add/Merge clips (`+`), Speed (`0.25x`–`4.0x`), Rotate (`90°`), Flip, Aspect Ratio (`9:16`, `16:9`, `1:1`, `4:5`)
  - **Audio**: Background music picker, Original & Music volume sliders, Fade In/Out, Noise Reduction (`afftdn`)
  - **Text**: Custom Bengali/English text overlay, color picker, font size, timing controls
  - **Overlay (PIP)**: Image/Logo overlay picker with corner placement & scale
  - **Effects & Filters**: Brightness, Contrast, Saturation, Vignette, Sharpen, B&W, Vintage presets
  - **Undo / Redo** history stack
- **Watermark-Free Export**: Export in `720p`, `1080p`, `2K`, or `4K` at `24/30/60fps` directly to `Movies/VisionCut`.

## Build APK via GitHub Actions (No Android Studio Needed)
1. Create a new repository on GitHub (e.g., `VisionCut-Android`).
2. Unzip `VisionCut-Android-MVP-v1.zip` and upload all files/folders.
3. **Important:** Browser drag-and-drop ignores hidden folders like `.github`. If `.github/workflows/build-apk.yml` is missing after upload, click **Add file → Create new file**, type `.github/workflows/build-apk.yml`, paste its content from this folder, and commit.
4. Open the **Actions** tab → **Build VisionCut Android APK** → download `VisionCut-v1.0-arm64-APK`.
