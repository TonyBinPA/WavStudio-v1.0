# WavStudio

An audio and video editor for Android, built for **Android 17 (API 37) on Samsung One UI 9**. It runs on Android 10 and later.

## Features

| Feature | How |
|---|---|
| **Trim clips** | Select a clip, then drag the trim handles, or use *Set in/out at playhead* while previewing. |
| **Split clips** | *Split at playhead* cuts a clip into two parts you can arrange separately. |
| **Combine clips** | Add more clips to the video track (they play one after another). Use *Move earlier/later*, *Duplicate* or *Delete* to rearrange. |
| **Extra audio tracks** | *Add audio track* adds music, voice-over or sound effects that play over the video. Each track has its own volume, mute and start delay. |
| **Separate audio from video** | *Detach audio to new track* mutes the video clip and puts its sound on its own track, still in sync. *Extract audio file…* saves just the clip's audio as a separate file. |
| **Convert formats** | Export as **MP4** (H.264, H.265/HEVC or AV1), **WebM** (VP9 + Opus), **M4A** (AAC), **WAV** (16-bit PCM) or **OGG** (Opus). You can also pick the resolution (Original down to 480p) and the audio bitrate. |
| **Preview** | *Clip* mode plays the selected clip with its trim applied. *Project* mode plays the whole edit, with all tracks mixed. |
| Undo / redo, autosave | Every edit can be undone. The project is saved automatically and restored when you reopen the app. |

Exports run in a foreground service (type `mediaProcessing`), so they keep going if you leave the app. Finished files go to **Movies/WavStudio** (video) or **Music/WavStudio** (audio), where the Samsung Gallery, My Files and music apps can find them.

### Built for One UI 9 / Android 17
- Targets and compiles against API 37 (Android 17).
- Material 3 with dynamic colour, so it uses your One UI colour palette. It also supports dark mode, edge-to-edge and predictive back.
- Adaptive layout: on a Galaxy Z Fold's inner screen, a Galaxy Tab or in DeX, the preview and timeline sit side by side.
- Needs no storage permission. Media is opened through the system picker, and exports are saved through MediaStore.

## Building

You need JDK 17 or newer and the Android SDK (platform `android-37.2`, build-tools 37).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # minified; signed with the debug key for sideloading
./gradlew testDebugUnitTest lintDebug
```

To install on a phone with USB debugging turned on, run `adb install app/build/outputs/apk/release/app-release.apk`. Before publishing to Galaxy Store or Google Play, replace the debug signing config in `app/build.gradle.kts` with your own key.

## Architecture

- `model/`: an immutable `Project` → `Track` → `Clip` model. `ProjectOps` holds the editing operations as pure functions, which are unit tested. `ProjectStore` handles JSON autosave.
- `export/CompositionFactory`: turns the project into a Media3 `Composition`. The video track becomes the main sequence (each clip trimmed with a `ClippingConfiguration` and scaled to one frame size with `Presentation`). Each audio track becomes a mixed audio-only sequence, with gaps for start delays and gain for volume.
- `export/Exporter`: renders with **Media3 Transformer** (hardware encoders).
  - WebM and OGG are written through `FrameworkContainerMuxer`, which wraps the platform `MediaMuxer`.
  - WAV is made by rendering a 320 kbps AAC file and then decoding it to PCM (`WavDecoder`).
- `export/ExportManager` + `ExportService`: run the export in the background and show progress notifications.
- `ui/`: Jetpack Compose screens. The preview uses ExoPlayer (clip mode) and Media3 `CompositionPlayer` (project mode).

## Known limitations
- The device's encoders decide which formats it can write. If a device has no AV1 or HEVC encoder, Transformer falls back to a supported codec.
- WAV export goes through a high-bitrate AAC step, so it isn't bit-exact with lossless sources.
- MP3 export isn't offered because Android has no built-in MP3 encoder. MP3 files work fine as input.
