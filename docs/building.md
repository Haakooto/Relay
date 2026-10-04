# Building and installing Relay

There are two ways to get Relay onto an Android phone: **install a ready-made APK** (no tools
needed), or **build it yourself** from this repository.

Relay needs **Android 13 or newer**.

---

## Option 1: install a ready-made APK

If someone sent you `app-release.apk` (or a `.zip` with it inside):

1. Open the file on the phone. Unzip it first if it came as a zip.
2. Android asks to allow installs from that app (your browser, Files, …). Allow it.
3. If Play Protect warns about an unknown app, choose **Install anyway**. This is normal for
   apps from outside the Play Store.
4. Open Relay. The red banner on the Alarms pane walks you through the two permissions that
   need a tap: notifications, and showing alarms over the lock screen.

Updates install the same way, over the old version, keeping your alarms and timers. That only
works if the update was signed by the same person as the original.

---

## Option 2: build it yourself

### What you need

- **Android Studio** (latest stable), which includes the Android SDK and a Java runtime.
  Without Android Studio you need **JDK 17 or newer** and the **Android SDK** (platform 35 and
  build tools).
- **git**, to get the code.
- An **Android 13+ phone** with developer options on (see "Installing on your phone" below).

The project brings its own Gradle (the `gradlew` wrapper), so you don't install Gradle
separately.

### Get the code

```
git clone <repository URL>
cd Relay
```

### Build with Android Studio (easiest)

1. **File → Open** and pick the `Relay` folder.
2. Wait for "Gradle sync" to finish. The first run downloads a lot, which takes a few minutes.
3. Plug in the phone (or pair it over Wi-Fi, see below), pick it in the device menu at the top,
   and press **Run ▶**.

This installs the **debug build** as **"Relay Debug"**. It's a complete app, just signed with a
throwaway development key.

### Build from the command line

Tell the build where the Android SDK is: either set `ANDROID_HOME`, or create
`local.properties` in the project folder with:

```
sdk.dir=/path/to/Android/Sdk
```

Android Studio creates that file for you. Then:

```
./gradlew :app:assembleDebug      # build the debug APK
./gradlew :app:installDebug       # build and install it on a connected phone
```

The debug APK ends up in `app/build/outputs/apk/debug/app-debug.apk`.

### Installing on your phone

1. **Settings → About phone →** tap **Build number** seven times to turn on developer options.
2. **Settings → System → Developer options →** turn on **USB debugging** (for a cable) or
   **Wireless debugging** (for Wi-Fi; pair it from Android Studio's device manager).
3. Connect, accept the "Allow debugging?" prompt on the phone, and run or install as above.

### A release build of your own

A debug build is fine for personal use. To make a proper release, the same kind you'd share,
you need your own signing key:

1. Make a key once, in a normal terminal (it asks for a password of at least 6 characters):
   ```
   keytool -genkeypair -v -keystore relay-release.jks -alias relay -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Copy `keystore.properties.example` to `keystore.properties` and fill in the key's path and
   password. Write the password without quotes, exactly as typed. Both files are git-ignored.
3. Build:
   ```
   ./gradlew :app:assembleRelease
   ```
   The result is `app/build/outputs/apk/release/app-release.apk`: signed, shrunk to about 5 MB,
   and installable on any Android 13+ phone.

**Keep the key file and its password safe.** Every update must be signed with the same key, or
phones refuse to install it over the old version.

Release numbers come from git tags: tag a commit `vX.Y` (for example `git tag -a v1.1 -m
"v1.1"`) before building a release you want to share, so that phones accept it as an update.

### Tests

```
./gradlew :app:testDebugUnitTest      # app logic: scheduling, runs, relays, stopwatch, editor
./gradlew :chainlang:test             # the relay language
./gradlew :app:connectedAndroidTest   # database migrations, on a connected phone or emulator
```

---

## Troubleshooting

- **"App not installed" when updating:** the new APK was signed with a different key than the
  one installed (for example, a debug build over a release build). Uninstall the old one first.
  This deletes its data.
- **Gradle sync fails right after moving the project folder:** open the folder again with
  **File → Open**. Don't use Recent Projects, which still points at the old path.
- **"SDK location not found":** create `local.properties` with `sdk.dir=…` (see above).
- **Alarms ring late or not at all:** check the red banner on the Alarms pane, and turn off
  battery optimisation for Relay if the phone asks.
