import 'package:flutter_test/flutter_test.dart';
import 'package:my_stt_recorder/my_stt_recorder.dart';

void main() {
  test('preserves saved audio metadata independently from speech failure', () {
    final event = RecorderEvent.fromMap({
      'type': 'stopped',
      'audioPath': '/data/user/0/example/files/note.wav',
      'durationMs': 1234,
      'transcript': 'Hello',
      'speechComplete': false,
      'audioComplete': true,
    }) as RecordingStopped;
    expect(event.result.duration, const Duration(milliseconds: 1234));
    expect(event.result.audioComplete, isTrue);
    expect(event.result.speechComplete, isFalse);
    expect(event.result.transcript, 'Hello');
    expect(event.result.audioPath, endsWith('note.wav'));
  });

  test('preserves partial versus committed transcript events', () {
    final partial = RecorderEvent.fromMap({
      'type': 'transcript', 'text': 'Hello wor', 'isFinal': false,
    }) as TranscriptChanged;
    expect(partial.isFinal, isFalse);
    expect(partial.text, 'Hello wor');
    final committed = RecorderEvent.fromMap({
      'type': 'transcript', 'text': 'Hello world', 'isFinal': true,
    }) as TranscriptChanged;
    expect(committed.isFinal, isTrue);
  });

  test('rejects unexpected native protocol events', () {
    expect(() => RecorderEvent.fromMap({'type': 'unsupported'}), throwsFormatException);
  });
}
