# SM Assistant — Android app

Native Android app for SM Assistant, built with Kotlin and Jetpack Compose (Material 3).
Requirements live in [`../docs/requirements/`](../docs/requirements/README.md).

## Stack

| | |
|---|---|
| Language | Kotlin 2.2 |
| UI | Jetpack Compose (BOM 2025.07), Material 3 |
| Navigation | Navigation Compose |
| Build | Gradle 8.14.3 (wrapper), AGP 8.11, version catalog in `gradle/libs.versions.toml` |
| SDK | minSdk 26 (Android 8.0), target/compile 36 |
| JDK | 17+ |

## Run

Open the `android/` folder in Android Studio and run the `app` configuration, or from the command line:

```sh
cd android
./gradlew :app:installDebug
```

## Layout

```
app/src/main/java/com/smassistant/app/
  MainActivity.kt
  navigation/   routes (Routes.kt) and the NavHost
  ui/theme/     colours from the wireframes, Material theme
  ui/components SmScreen frame (app bar, section chip, Back to Home), buttons
  ui/screens/   one composable per screen in the screen inventory (S1–S14)
```

## Status

Skeleton only: every screen from the requirements exists and is reachable, but nothing is saved
or sent yet. Settings SAVE, AI generation, hashtag persistence and LinkedIn posting are TODOs.

Choices made on open questions for now:
- Q-01: Home shows a single **AI Agent** button.
- Q-02: The hub also has **Back to Home**.
- Q-17: The hashtag toggle sits on the Default Hash Tags screen.
