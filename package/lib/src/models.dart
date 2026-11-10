/// Native events are delivered on a shared broadcast stream.
sealed class RecorderEvent {
  const RecorderEvent();

  factory RecorderEvent.fromMap(Map<Object?, Object?> map) {
    switch (map['type']) {
      case 'state':
        return RecorderStateChanged(map['state']! as String);
      case 'transcript':
        return TranscriptChanged(
          text: map['text']! as String,
          isFinal: map['isFinal']! as bool,
        );
      case 'level':
        return AudioLevel((map['value']! as num).toDouble());
      case 'issue':
        return RecorderIssue(
          code: map['code']! as String,
          message: map['message']! as String,
        );
      case 'stopped':
        return RecordingStopped(RecordingResult.fromMap(map));
      default:
        throw FormatException('Unknown recorder event: ${map['type']}');
    }
  }
}

final class RecorderStateChanged extends RecorderEvent {
  const RecorderStateChanged(this.state);
  /// starting, recording, stopping, idle.
  final String state;
}

final class TranscriptChanged extends RecorderEvent {
  const TranscriptChanged({required this.text, required this.isFinal});
  /// All committed segments plus the current partial segment.
  final String text;
  /// True when this event commits a segment; more segments may follow.
  final bool isFinal;
}

final class AudioLevel extends RecorderEvent {
  const AudioLevel(this.value);
  /// PCM peak level in [0, 1], throttled to about 10 events per second.
  final double value;
}

final class RecorderIssue extends RecorderEvent {
  const RecorderIssue({required this.code, required this.message});
  final String code;
  final String message;
}

final class RecordingStopped extends RecorderEvent {
  const RecordingStopped(this.result);
  final RecordingResult result;
}

final class RecordingResult {
  const RecordingResult({
    required this.audioPath,
    required this.duration,
    required this.transcript,
    required this.speechComplete,
    required this.audioComplete,
  });

  factory RecordingResult.fromMap(Map<Object?, Object?> map) => RecordingResult(
        audioPath: map['audioPath']! as String,
        duration: Duration(milliseconds: (map['durationMs']! as num).toInt()),
        transcript: map['transcript']! as String,
        speechComplete: map['speechComplete']! as bool,
        audioComplete: map['audioComplete']! as bool,
      );

  /// App-private persistent files directory. Delete recordings when no longer needed.
  final String audioPath;
  /// Duration calculated from recorded samples, excluding recognition finalization.
  final Duration duration;
  /// Committed segments only. Incomplete partial text is excluded.
  final String transcript;
  /// Whether the speech service completed normally before the finalization deadline.
  /// This does not establish transcript accuracy or audio-injection support.
  final bool speechComplete;
  /// False after a capture or file-write failure; audio may contain only a prefix.
  final bool audioComplete;
}
