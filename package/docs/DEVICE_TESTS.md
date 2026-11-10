# Required Android device checks

Record device model, Android API, Flutter version, recognition service/version, locale and on-device/network mode alongside each result. These checks have not been performed in this environment.

| Check | Expected result |
|---|---|
| Fresh install; grant microphone | Start succeeds after permission and level responds |
| Deny permission; deny permanently | Actionable message; no mic capture or stuck busy state |
| Missing recognition service | Start reports unavailable without creating an active recording |
| 10-second spoken recording | WAV is 16 kHz PCM16 mono, nonempty, audible and duration matches speech session |
| Partial and segmented text | Partial updates replace provisional text; committed segments accumulate |
| Silence and long pauses | Device behavior is recorded; early speech end is reported, WAV continues |
| Speech service ignores injected source | Treat that service/device combination as unsupported; do not advertise compatibility |
| English/Malayalam/Hindi | Validate each claimed language; unsupported ones produce a visible issue |
| On-device mode without a model | Clear native error; no claim of offline transcription |
| Network loss during recognition | Recognition issue; WAV continues and can be saved |
| Rapid double start/stop | One active session; no duplicate mic owners, no stuck UI |
| Stop during an outstanding stop | Both waiters complete with the same finalized result |
| Cancel during recording or stopping | Current file deleted; pending stop returns cancelled |
| New session after cancel/stop/error | Unique file; old callbacks cannot alter new transcript/state |
| Lock screen / Home / rotation | Foreground session stops; stopped result arrives; recording can be restarted |
| Phone call, audio focus loss, mic privacy switch | Inspect recording and error reporting; document device behavior |
| Bluetooth headset connection/disconnection | Inspect route, levels and saved audio; recovery not implemented |
| Recognition service stalled or pipe unread | Speech issue without blocking capture or the Flutter UI |
| Disk full / write failure | Audio failure reported, capture ends and audioComplete is false |
| Leave screen while startup is pending | Controller releases any session that finishes starting after disposal |
| Force-stop the process | Known limitation: unfinished WAV header may need recovery |

Before a release, add Android instrumentation tests for session state/race handling and measure memory/CPU over representative session lengths. Do not substitute desktop WAV tests for device tests.
