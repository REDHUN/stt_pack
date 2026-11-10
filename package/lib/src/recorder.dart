import 'dart:async';

import 'package:flutter/services.dart';

import 'models.dart';

/// One recorder per Flutter engine. The package owns the microphone natively.
final class MySttRecorder {
  MySttRecorder._();
  static final MySttRecorder instance = MySttRecorder._();
  static const _methods = MethodChannel('my_stt_recorder/methods');
  static const _nativeEvents = EventChannel('my_stt_recorder/events');

  final _events = StreamController<RecorderEvent>.broadcast();
  StreamSubscription<dynamic>? _subscription;
  Future<void>? _initialization;

  Stream<RecorderEvent> get events => _events.stream;

  /// Subscribe to [events] before starting if you need every event.
  Future<void> initialize() => _initialization ??= _connect();

  Future<void> _connect() async {
    final ready = Completer<void>();
    _subscription = _nativeEvents.receiveBroadcastStream().listen((dynamic raw) {
      final map = Map<Object?, Object?>.from(raw as Map);
      if (map['type'] == 'ready') {
        if (!ready.isCompleted) ready.complete();
        return;
      }
      try {
        _events.add(RecorderEvent.fromMap(map));
      } catch (error, stack) {
        _events.addError(error, stack);
      }
    }, onError: (Object error, StackTrace stack) {
      if (!ready.isCompleted) {
        ready.completeError(error, stack);
      } else {
        _events.addError(error, stack);
      }
    });
    try {
      await ready.future.timeout(const Duration(seconds: 5));
    } catch (_) {
      await _subscription?.cancel();
      _subscription = null;
      _initialization = null;
      rethrow;
    }
  }

  Future<bool> hasPermission() async =>
      await _methods.invokeMethod<bool>('hasPermission') ?? false;

  /// Call from a visible activity in response to a user action.
  Future<bool> requestPermission() async =>
      await _methods.invokeMethod<bool>('requestPermission') ?? false;

  Future<bool> isRecognitionAvailable() async =>
      await _methods.invokeMethod<bool>('isRecognitionAvailable') ?? false;

  /// Android 13+, foreground only. Throws if already running or unavailable.
  /// [onDevice] requires an installed on-device speech recognizer/model.
  /// Language support and audio-source support depend on the speech service.
  Future<void> start({String localeId = 'en-IN', bool onDevice = false}) async {
    if (localeId.trim().isEmpty) {
      throw ArgumentError.value(localeId, 'localeId', 'Cannot be empty');
    }
    await initialize();
    await _methods.invokeMethod<void>('start', {
      'localeId': localeId,
      'onDevice': onDevice,
    });
  }

  /// Finalizes WAV, then waits briefly for final speech results.
  /// Idempotent after the first completed session; returns its saved result.
  Future<RecordingResult> stop() async {
    final map = await _methods.invokeMapMethod<Object?, Object?>('stop');
    if (map == null) throw StateError('Native recorder returned no result.');
    return RecordingResult.fromMap(map);
  }

  /// Removes the current session file; does not delete older recordings.
  Future<void> cancel() => _methods.invokeMethod<void>('cancel');
}
