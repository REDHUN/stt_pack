package com.redhun.my_stt_recorder

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import java.io.File
import java.util.UUID

/** Android 13+ foreground MVP. All state mutations and Flutter calls run on main. */
class MySttRecorderPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler,
    EventChannel.StreamHandler, PluginRegistry.RequestPermissionsResultListener,
    Application.ActivityLifecycleCallbacks {
    private lateinit var context: Context
    private lateinit var methods: MethodChannel
    private lateinit var events: EventChannel
    private val main = Handler(Looper.getMainLooper())
    private var sink: EventChannel.EventSink? = null
    private var activity: Activity? = null
    private var binding: ActivityPluginBinding? = null
    private var foreground = false
    private var permissionResult: MethodChannel.Result? = null
    private var capture: CaptureSession? = null
    private var recognizer: SpeechRecognizer? = null
    private var token = 0L
    private var stopping = false
    private var cancelled = false
    private var audioDone = false
    private var audioComplete = true
    private var durationMs = 0L
    private var speechDone = false
    private var speechComplete = false
    private var committed = ""
    private var lastResult: Map<String, Any>? = null
    private val stopResults = mutableListOf<MethodChannel.Result>()
    private val cancelResults = mutableListOf<MethodChannel.Result>()
    private var deadline: Runnable? = null

    override fun onAttachedToEngine(b: FlutterPlugin.FlutterPluginBinding) {
        context = b.applicationContext
        methods = MethodChannel(b.binaryMessenger, "my_stt_recorder/methods")
        events = EventChannel(b.binaryMessenger, "my_stt_recorder/events")
        methods.setMethodCallHandler(this)
        events.setStreamHandler(this)
        (context as Application).registerActivityLifecycleCallbacks(this)
    }

    override fun onListen(arguments: Any?, eventSink: EventChannel.EventSink) {
        sink = eventSink
        emit(mapOf("type" to "ready"))
    }
    override fun onCancel(arguments: Any?) { sink = null }
    private fun emit(event: Map<String, Any>) { sink?.success(event) }
    private fun state(value: String) = emit(mapOf("type" to "state", "state" to value))
    private fun issue(code: String, message: String) = emit(mapOf(
        "type" to "issue", "code" to code, "message" to message,
    ))
    private fun hasPermission() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "hasPermission" -> result.success(hasPermission())
            "isRecognitionAvailable" -> result.success(SpeechRecognizer.isRecognitionAvailable(context))
            "requestPermission" -> {
                when {
                    hasPermission() -> result.success(true)
                    activity == null -> result.error("no_activity", "Open the app before requesting permission.", null)
                    permissionResult != null -> result.error("permission_busy", "A permission request is already pending.", null)
                    else -> {
                        permissionResult = result
                        activity!!.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 49121)
                    }
                }
            }
            "start" -> start(call, result)
            "stop" -> {
                if (capture == null) {
                    if (lastResult != null) result.success(lastResult)
                    else result.error("not_recording", "There is no saved recording in this session.", null)
                } else if (cancelled) {
                    result.error("cancelled", "This recording is being cancelled.", null)
                } else {
                    stopResults.add(result)
                    beginStop()
                }
            }
            "cancel" -> {
                if (capture == null) result.success(null)
                else {
                    cancelled = true
                    cancelResults.add(result)
                    speechDone = true
                    speechComplete = false
                    capture?.disableSpeech()
                    recognizer?.cancel()
                    beginStop()
                    finishIfReady()
                }
            }
            else -> result.notImplemented()
        }
    }

    private fun start(call: MethodCall, result: MethodChannel.Result) {
        if (capture != null) { result.error("busy", "Stop the current recording first.", null); return }
        if (activity == null || !foreground) { result.error("not_foreground", "Start recording while the app is visible.", null); return }
        if (!hasPermission()) { result.error("permission_denied", "Microphone permission is required.", null); return }
        if (sink == null) { result.error("no_listener", "Initialize the event stream before starting.", null); return }
        val locale = call.argument<String>("localeId")?.trim().orEmpty()
        if (locale.isEmpty()) { result.error("invalid_locale", "Provide a language tag such as en-IN.", null); return }
        val onDevice = call.argument<Boolean>("onDevice") ?: false
        if (onDevice && !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            result.error("on_device_unavailable", "No on-device speech recognizer is available.", null); return
        }
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(context)) {
            result.error("stt_unavailable", "No speech recognition service is installed.", null); return
        }
        token++
        val id = token
        stopping = false; cancelled = false; audioDone = false
        audioComplete = true; durationMs = 0; speechDone = false
        speechComplete = false; committed = ""; lastResult = null
        state("starting")
        try {
            val directory = File(context.filesDir, "my_stt_recorder")
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create recording directory" }
            val file = File(directory, "${UUID.randomUUID()}.wav")
            val session = CaptureSession(file,
                onLevel = { level -> main.post { if (id == token && capture != null && !stopping) emit(mapOf("type" to "level", "value" to level)) } },
                onIssue = { code, message -> main.post {
                    if (id == token && capture != null) {
                        issue(code, message)
                        if (code == "speech_pipe_failed") failSpeech()
                    }
                } },
                onAudioDone = { duration, complete -> main.post {
                    if (id == token && capture != null) {
                        durationMs = duration; audioComplete = complete; audioDone = true
                        if (!stopping) beginStop()
                        finishIfReady()
                    }
                } },
            )
            capture = session
            recognizer = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer!!.setRecognitionListener(listener(id))
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, session.speechInput)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, CaptureSession.RATE)
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            }
            // Start capture before asking the service to consume the pipe.
            session.start()
            recognizer!!.startListening(intent)
            state("recording")
            result.success(null)
        } catch (error: Exception) {
            // Capture may already be running. Finalize asynchronously and preserve any audio.
            issue("start_failed", error.message ?: "Could not start recording")
            if (capture != null) {
                speechDone = true
                capture?.disableSpeech()
                // start() cleans up on failure; if it did not start, no completion callback follows.
                if (!capture!!.started) {
                    capture?.abortBeforeStart(); capture = null
                    cleanupRecognizer(); state("idle")
                } else beginStop()
            } else state("idle")
            result.error("start_failed", error.message ?: "Could not start recording", null)
        }
    }

    private fun listener(id: Long) = object : RecognitionListener {
        private fun current() = id == token && capture != null && !cancelled
        private fun text(results: Bundle?) = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        private fun transcript(results: Bundle?, final: Boolean) {
            if (!current() || speechDone) return
            val segment = text(results).trim()
            if (final && segment.isNotEmpty()) committed = listOf(committed, segment).filter { it.isNotEmpty() }.joinToString(" ")
            val display = if (final) committed else listOf(committed, segment).filter { it.isNotEmpty() }.joinToString(" ")
            emit(mapOf("type" to "transcript", "text" to display, "isFinal" to final))
        }
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {} // Never used as an audio recording source.
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) = transcript(partialResults, false)
        override fun onSegmentResults(segmentResults: Bundle) = transcript(segmentResults, true)
        override fun onResults(results: Bundle?) {
            if (!current() || speechDone) return
            transcript(results, true)
            completeSpeech()
        }
        override fun onEndOfSegmentedSession() { if (current() && !speechDone) completeSpeech() }
        private fun completeSpeech() {
            speechDone = true; speechComplete = true
            capture?.disableSpeech()
            if (!stopping) issue("speech_ended", "The speech service ended its session. Audio recording continues; stop and start a new session to transcribe more speech.")
            finishIfReady()
        }
        override fun onError(error: Int) {
            if (!current() || speechDone) return
            issue("speech_error_$error", "Speech recognition failed (Android code $error). Audio recording continues until stopped.")
            failSpeech()
        }
    }

    private fun failSpeech() {
        speechDone = true; speechComplete = false
        capture?.disableSpeech()
        runCatching { recognizer?.cancel() }
        finishIfReady()
    }

    private fun beginStop() {
        if (stopping) return
        stopping = true
        state("stopping")
        capture?.stop() // Nonblocking read loop exits; worker writes WAV header.
        val id = token
        val timeout = Runnable {
            if (id == token && capture != null && !speechDone) {
                issue("speech_finalize_timeout", "Final transcript did not arrive within 4 seconds. The recording is retained.")
                failSpeech()
            }
        }
        deadline = timeout
        main.postDelayed(timeout, 4000)
        finishIfReady()
    }

    private fun finishIfReady() {
        if (!stopping || !audioDone || !speechDone) return
        deadline?.let { main.removeCallbacks(it) }; deadline = null
        val session = capture ?: return
        capture = null
        session.disableSpeech(); session.closeInput()
        cleanupRecognizer()
        if (cancelled) {
            val deleted = !session.file.exists() || session.file.delete()
            lastResult = null
            stopResults.forEach { it.error("cancelled", "Recording was cancelled.", null) }
            cancelResults.forEach {
                if (deleted) it.success(null)
                else it.error("delete_failed", "Could not delete ${session.file.path}", null)
            }
        } else {
            val saved = mapOf<String, Any>(
                "audioPath" to session.file.absolutePath, "durationMs" to durationMs,
                "transcript" to committed, "speechComplete" to speechComplete,
                "audioComplete" to audioComplete,
            )
            lastResult = saved
            emit(saved + ("type" to "stopped"))
            stopResults.forEach { it.success(saved) }
        }
        stopResults.clear(); cancelResults.clear()
        state("idle")
    }

    private fun cleanupRecognizer() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    override fun onAttachedToActivity(b: ActivityPluginBinding) {
        binding = b; activity = b.activity
        foreground = activity?.hasWindowFocus() == true
        b.addRequestPermissionsResultListener(this)
    }
    private fun detachActivity() {
        binding?.removeRequestPermissionsResultListener(this)
        permissionResult?.error("activity_detached", "Activity detached during permission request.", null)
        permissionResult = null
        if (capture != null) beginStop()
        foreground = false; activity = null; binding = null
    }
    override fun onDetachedFromActivityForConfigChanges() = detachActivity()
    override fun onReattachedToActivityForConfigChanges(b: ActivityPluginBinding) = onAttachedToActivity(b)
    override fun onDetachedFromActivity() = detachActivity()
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        if (requestCode != 49121) return false
        permissionResult?.success(hasPermission()); permissionResult = null
        return true
    }
    override fun onActivityResumed(a: Activity) { if (a == activity) foreground = true }
    override fun onActivityPaused(a: Activity) {
        if (a == activity) {
            foreground = false
            if (capture != null) { issue("app_paused", "Recording stopped because the app left the foreground."); beginStop() }
        }
    }
    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityStarted(a: Activity) {}
    override fun onActivityStopped(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity) {}
    override fun onDetachedFromEngine(b: FlutterPlugin.FlutterPluginBinding) {
        detachActivity()
        methods.setMethodCallHandler(null); events.setStreamHandler(null); sink = null
        (context as Application).unregisterActivityLifecycleCallbacks(this)
        // The capture worker still owns its resources and finalizes WAV after stop().
        // Never block Flutter's main thread joining recording/pipe workers.
        if (capture != null) { speechDone = true; capture?.disableSpeech(); beginStop(); finishIfReady() }
        cleanupRecognizer()
    }
}
