# RAM Watch
Lightweight Android app (~30 KB, no libraries) that pins live available RAM to the notification bar.

## Get the APK (free, ~3 min, no PC needed)
1. On github.com create a new repository, e.g. `ramwatch`.
2. Click "uploading an existing file", upload ALL files from this zip (keep folders, including `.github`).
3. Open the **Actions** tab -> "Build APK" runs automatically (or click "Run workflow").
4. When it shows a green tick, open the run -> **Artifacts** -> download `RamWatch-apk` -> unzip -> install `RamWatch.apk`.

## Features
- Notification: "Free RAM: 2.31 GB (29%)" + "Used X of Y" + progress bar, updates every 2 s
- Pauses updates when screen is off (near-zero battery)
- Silent, low-priority, can't be swiped away; tap opens the app
- Auto-starts after reboot if it was pinned
- Android 7 to 15
