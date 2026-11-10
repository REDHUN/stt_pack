# my_stt_recorder — Android-first starter

A new Flutter plugin implementing simultaneous WAV recording and live speech recognition with a single native microphone capture. This project is an original starter, not a copy of `stt_record` source, and does not depend on `stt_record`.

**Status: source starter requiring Flutter build and real-device validation.** This environment did not have Flutter, Dart, an Android SDK or an emulator. The production WAV writer was compiled and executed on Java 17; Flutter tests and Android integration were not run here. Do not treat this as a production release or a guarantee of device compatibility.

## Included

- Dart API with typed transcript, state, microphone level, issue and saved-recording events.
- Kotlin Android plugin using `AudioRecord`: 16 kHz, PCM16, mono WAV.
- One audio capture stream feeds the file writer and an Android speech-recognition pipe.
- Partial and committed transcript segments, plus aggregated final text.
- Bounded speech queue and nonblocking pipe writes so stalled transcription does not block recording.
- Start, stop, cancel, runtime microphone permission and foreground lifecycle handling.
- App-private persistent recordings with unique filenames.
- Material 3 example using Provider; English (India), Malayalam and Hindi options.
- Java WAV checks, Dart model tests and a device-validation checklist.

This first version supports **Android 13/API 33 and later only**. iOS, web, desktop, pause/resume, background recording, automatic recognizer restarts and audio playback are not implemented. No iOS platform registration is advertised.

## 1. Requirements

- Flutter stable with Dart >= 3.4; Android Studio and a working `flutter doctor`.
- Java 17; Android SDK Platform 35 (or a newer compile SDK chosen by your host app).
- A physical Android 13+ device with an installed speech recognition service.
- Python 3 for the optional example-host bootstrap command below.

The plugin's Android module pins AGP 8.7.3 and Kotlin 2.1.0 as a starting configuration. Your host app's Gradle, AGP and Kotlin versions must be compatible. Flutter generates the example host using your installed SDK's template, rather than shipping a potentially stale Gradle wrapper.

## 2. Run the example

Extract this folder, then run these commands from **my_stt_recorder**:

```bash
flutter pub get
flutter test
python tools/bootstrap_example.py
cd example
flutter pub get
flutter run
```

On Windows, use `py tools/bootstrap_example.py` if `python` is not available.

The bootstrap script generates `example/android`, preserves the provided Provider UI and pubspec, and sets the host app's minimum Android SDK to 33. It is repeatable, but review generated Android files before applying it to an existing customized example host.

If you prefer manual setup:

1. Back up `example/lib` and `example/pubspec.yaml`.
2. Run `flutter create --platforms=android --org com.redhun --project-name my_stt_recorder_example --no-pub example` from this package root.
3. Restore the backed-up demo files if the Flutter template changed them.
4. Set `minSdk = 33` in `example/android/app/build.gradle.kts`, or `minSdkVersion 33` in a Groovy build file.
5. Remove the generated counter-app widget test, then run the example commands above.

On the device, select a language, tap **Start recording**, allow microphone access, speak, then tap **Stop & save**. The app shows the transcript and private WAV path. Language availability is determined by the speech service, not by the language selector.

## 3. Use in your own app

Keep this package beside your Flutter app and add:

```yaml
dependencies:
  my_stt_recorder:
    path: ../my_stt_recorder
```

Set your Android app's minimum SDK to 33. The package manifest contributes microphone permission and speech-service visibility declarations.

```dart
import 'dart:async';
import 'package:my_stt_recorder/my_stt_recorder.dart';

final recorder = MySttRecorder.instance;
StreamSubscription<RecorderEvent>? subscription;

Future<void> startNote() async {
  subscription = recorder.events.listen((event) {
    if (event is TranscriptChanged) {
      // event.text contains committed segments plus the current partial segment.
      print(event.text);
    } else if (event is RecorderIssue) {
      print('${event.code}: ${event.message}');
    } else if (event is RecordingStopped) {
      // Also fires for automatic foreground/lifecycle stops.
      print(event.result.audioPath);
    }
  });
  if (!await recorder.requestPermission()) {
    await subscription?.cancel();
    return;
  }
  await recorder.start(localeId: 'en-IN');
}

Future<RecordingResult> stopNote() async {
  final saved = await recorder.stop();
  await subscription?.cancel();
  return saved;
}
```

Use one recorder instance/session per Flutter engine. Subscribe once per screen/controller and cancel subscriptions when finished. Handle `PlatformException` from permissions/start/stop in your UI. The included Provider controller demonstrates this handling. `cancel()` discards only the active recording; it does not delete previously saved files.

Provider belongs in the app. The plugin does not require Provider and can be used with another state manager.

## 4. Platform limitations

Android's `EXTRA_AUDIO_SOURCE` accepts a file descriptor from API 33. Android explicitly documents that a recognizer **may ignore this input and open the microphone itself**. Consequently, this starter cannot guarantee single-capture recognition on every service/device. Confirm it on each intended device and recognition service. A successful start or `speechComplete: true` does not prove audio-source support.

The speech service can finish early, reject a language, require a network, or fail. In those cases recording continues and an issue event explains what happened. There is no silent restart loop. Committed text is preserved; partial text is not stored as final text.

By default recognition uses the device's configured service, which may send audio to remote servers. `start(onDevice: true)` selects Android's on-device recognizer when available, but availability does not establish that the requested language/model is installed. This starter does not download speech models or claim offline support for every language.

The plugin requests segmented recognition. Some services may ignore it. It does not provide unlimited transcription. Android's generic speech-recognition API is not intended as a universal continuous-dictation engine.

When the activity pauses (navigation away, screen locking, rotation or an interruption that pauses the activity), recording stops and the WAV is finalized. No foreground service or background microphone behavior is implemented. Other audio interruptions, Bluetooth route changes and microphone silencing require device validation; this starter does not implement audio-focus or route recovery.

`stop()` closes capture, finalizes the WAV and waits up to four seconds for speech completion. The final result has separate `audioComplete` and `speechComplete` flags. A WAV write failure can leave a partial/unreadable file; check `audioComplete` and issue events. Speech completeness is an engine-completion signal, not an accuracy guarantee.

Recordings live under the app's private files directory, not the public Downloads folder. Use Android Studio Device Explorer for debug access, or add an explicit export/share feature. Manage retention and delete unneeded files. Android app backup policy is controlled by your host application; configure it appropriately for private audio. Abrupt process termination is not crash-safe and can leave an unfinished WAV header.

If you need reliable long sessions across many devices, keep this capture/file architecture and replace platform recognition with an engine or streaming backend that explicitly accepts your PCM audio. That is a separate backend implementation with its own language, resource, privacy and cost requirements.

## 5. Verification

The production Java WAV writer was compiled and tested successfully. See `docs/VALIDATION.md` for actual results and the required next checks.

Run the platform-independent WAV check with Java 17:

```bash
java -m jdk.compiler/com.sun.tools.javac.Main -d .wav-check android/src/main/java/com/redhun/my_stt_recorder/WavWriter.java tools/WavWriterCheck.java
java -cp .wav-check com.redhun.my_stt_recorder.WavWriterCheck .wav-check/audio
```

With Flutter installed:

```bash
flutter analyze
flutter test
cd example
flutter analyze
flutter build apk --debug
```

Then complete `docs/DEVICE_TESTS.md` on a real phone. Passing file-format tests alone does not validate the microphone, Android speech service or Flutter bridge.

## Design references

- Flutter plugins: https://docs.flutter.dev/packages-and-plugins/developing-packages
- Android audio-source injection and segmented sessions: https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_AUDIO_SOURCE
- Android speech recognizer lifecycle/limitations: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Native audio capture: https://developer.android.com/reference/android/media/AudioRecord

## License and publishing

The generated starter is provided under the included MIT license. `publish_to: none` prevents accidental publication. Before publishing, choose a unique package name and Android namespace, complete device/build checks, fill in repository metadata and review platform compatibility and privacy behavior.
