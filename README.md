# Lean Optimizer

Client-side Fabric mod for **Minecraft 26.2** (Java 25) that keeps your FPS steady without touching the render loop, so it runs fine next to Sodium, Lithium and friends.

## What it does

- **Adaptive tuning** - averages your FPS over a few seconds. Below target it first lowers entity distance (cheap, no chunk reload), then render distance, one step at a time. With headroom it restores render distance first, then entity distance, never going above what *you* had set.
- **Unfocused cap** - limits the frame rate while the game window is not focused and restores your cap when you come back.
- **Respects your settings** - if you change render/entity distance yourself, that becomes the new baseline. Your values are restored when you leave a world, when the game closes, and on `/leanopt toggle`.

## Commands

- `/leanopt` - show current FPS, distances and the last change
- `/leanopt toggle` - enable/disable (restores your settings when disabling)
- `/leanopt reload` - re-read the config file

## Config: `config/lean-optimizer.json`

| Key | Default | Meaning |
|---|---|---|
| enabled | true | master switch |
| adaptiveTuning | true | FPS-based tuning |
| targetFps | 60 | FPS to stay above (with VSync, keep this under your refresh rate) |
| minRenderDistance | 6 | floor for render distance (chunks) |
| minEntityDistanceScaling | 0.5 | floor for entity distance (vanilla 0.5-5.0) |
| sampleSeconds | 8 | how long FPS must stay bad/good before acting |
| cooldownSeconds | 30 | minimum time between changes (render distance changes reload chunks) |
| limitWhenUnfocused | true | cap FPS when unfocused |
| unfocusedFpsLimit | 20 | the unfocused cap |

## Build

Requires JDK 25.

```
./gradlew build
```

The jar is in `build/libs/` (use the one without `-sources`). Drop it in `mods/` together with Fabric API for 26.2.

Toolchain (from the official Fabric 26.2 template): Loader 0.19.5, Loom 1.18-SNAPSHOT, Fabric API 0.161.0+26.2, Gradle 9.7.1.
