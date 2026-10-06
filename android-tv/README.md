# TubeTamer for Android TV

Native Android TV client for the TubeTamer server. See `../tasks/android-tv.md` for the plan.

## Build (Windows)

Gradle must run on JDK 17 or newer. The global `JAVA_HOME` can stay on JDK 8, use Android Studio's JDK for this project:

```bat
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

`local.properties` (not committed) holds `sdk.dir`. Release signing reads `keystore.properties` (not committed): `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

## Emulator

AVD `TubeTamer-TV` (Android 11 TV, x86). Start it from Android Studio's Device Manager, then run the app.
