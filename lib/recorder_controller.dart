import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:my_stt_recorder/my_stt_recorder.dart';

class RecorderController extends ChangeNotifier {
  RecorderController() {
    _subscription = _recorder.events.listen(_onEvent, onError: (Object error) {
      _issue(error.toString());
    });
  }

  final _recorder = MySttRecorder.instance;
  final level = ValueNotifier<double>(0);
  late final StreamSubscription<RecorderEvent> _subscription;
  bool _disposed = false;
  bool busy = false;
  String state = 'idle';
  String localeId = 'en-IN';
  String transcript = '';
  String? message;
  RecordingResult? result;

  bool get isRecording => state == 'recording';
  bool get canStart => !busy && state == 'idle';
  bool get canStop => !busy && isRecording;

  void _notify() { if (!_disposed) notifyListeners(); }
  void _issue(String text) { message = text; _notify(); }

  void _onEvent(RecorderEvent event) {
    if (_disposed) return;
    switch (event) {
      case AudioLevel(:final value):
        level.value = value;
        return;
      case RecorderStateChanged(:final state):
        this.state = state;
        if (state == 'idle') level.value = 0;
      case TranscriptChanged(:final text):
        transcript = text;
      case RecorderIssue(:final message):
        this.message = message;
      case RecordingStopped(:final result):
        this.result = result;
        transcript = result.transcript;
    }
    _notify();
  }

  void changeLocale(String value) {
    if (!canStart) return;
    localeId = value;
    _notify();
  }

  Future<void> start() async {
    if (!canStart) return;
    busy = true;
    message = null;
    _notify();
    try {
      if (!await _recorder.requestPermission()) {
        _issue('Microphone permission was not granted. You can enable it in Android app settings.');
        return;
      }
      if (_disposed) return;
      if (!await _recorder.isRecognitionAvailable()) {
        _issue('No speech recognition service is available on this device.');
        return;
      }
      if (_disposed) return;
      transcript = '';
      result = null;
      await _recorder.start(localeId: localeId);
      if (_disposed) await _recorder.stop();
    } catch (error) {
      _issue(error.toString());
    } finally {
      busy = false;
      _notify();
    }
  }

  Future<void> stop() async {
    if (!canStop) return;
    busy = true;
    _notify();
    try {
      result = await _recorder.stop();
      transcript = result!.transcript;
    } catch (error) {
      _issue(error.toString());
    } finally {
      busy = false;
      _notify();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    unawaited(_subscription.cancel());
    // Native lifecycle handling also stops the microphone when the app is paused.
    unawaited(_recorder.stop().then<void>((_) {}, onError: (Object _) {}));
    level.dispose();
    super.dispose();
  }
}
