# Flow

A draggable floating button for Android that sits over any app.
Tap it to open a quick panel.

## Features
- Volume up / down / mute
- Ringer mode cycle (ring → vibrate → silent)
- Flashlight toggle
- **Screen AI** (uses Claude): explain the screen, translate it, or suggest replies
- Copy AI answers to the clipboard

## Install
1. Push this repo to GitHub. The **Build APK** workflow runs automatically.
2. Open **Actions → latest run → Artifacts → Flow-apk** (or tag a release: `git tag v1.0.0 && git push --tags` and download from **Releases**).
3. Install the APK, open Flow, then:
   - Save your Anthropic API key (needed only for Screen AI)
   - Allow **Display over other apps**
   - Tap **Start floating button**

## Notes
- Screen AI captures a single screenshot only when you tap an AI button, and sends it to the Anthropic API.
- The API key is stored in app-private storage on your device. Never commit keys to the repo.
- Ringer "silent" needs Do Not Disturb access on some phones.
- Requires Android 8.0+.

## Build locally
`gradle assembleDebug` (Gradle 8.7, JDK 17, Android SDK 34)

Built by Chibuike. MIT licensed.
