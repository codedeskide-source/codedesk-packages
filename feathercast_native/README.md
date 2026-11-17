# FeatherCast Ninja v0.7 — Native Android camera / head + body test

**Native installable APK, built with Gradle + Android SDK.** No Chrome, Brave, Termux, login, PC or internet required to run the camera tracking after installation. The phone handles camera and avatar processing locally.

## What's new

- Independently animated ninja head and torso: ML Kit face tracking drives the head; ML Kit pose shoulders drive the torso.
- The ninja scales closer / farther as the *apparent size of the face* changes. This is relative camera depth, not an accurate physical-distance measurement.
- By default, the live mirrored camera fills the phone display and the character is overlaid at the detected face and shoulder positions. The **Avatar only** button hides the camera image while keeping tracking active.
- Shoulders and head are smoothed independently, with a fallback to a face-estimated shoulder position when shoulders are not visible.
- Original dark hood ninja in shadow/crimson/forest. Speaking changes the fabric mask shape; NO drawn mouth oval or line.
- Requests camera permission only. Face and pose detection use locally bundled ML Kit models and do not upload frames.

## Test on your phone

1. Install the APK. Android may ask for approval to install an APK from your download source.
2. Open FeatherCast Ninja and choose **Start camera**. Grant CAMERA permission when prompted.
3. In the live camera overlay, keep your head and both shoulders in view. The ninja's face and torso should align near yours. Turn your head while keeping shoulders still, then tilt shoulders while keeping head still.
4. Move closer to / farther from the phone: the character changes scale.
5. Tap **Avatar only** to hide selfie pixels, while still animating the ninja. Tap **Camera overlay** to return. Tap **Ninja color** to switch palettes.

**Early test limitations:** A single 2D front-facing torso is used; independent pose and facial motion are approximate, not full 3D skeletal motion. Extreme leans/occlusions and devices with unusual camera preview crops can cause small offset errors. Scale measures face size as a proxy for distance; it does not sense depth in meters. App isn't yet streaming to OBS/Twitch/YouTube/Rumble. Live on-device camera behavior needs a real Android hardware test.

## Source/build

Android Gradle Plugin 8.7.3 / Gradle 8.9 / JDK 17 / Android compile SDK 35.
Use `gradle assembleDebug`; result `app/build/outputs/apk/debug/app-debug.apk`.

Original FeatherCast artwork and code: CC0. Google ML Kit, CameraX, and other dependencies retain their respective licenses.