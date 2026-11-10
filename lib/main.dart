import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import 'recorder_controller.dart';

void main() => runApp(ChangeNotifierProvider(
      create: (_) => RecorderController(),
      child: const RecorderApp(),
    ));

class RecorderApp extends StatelessWidget {
  const RecorderApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'Voice Notes',
        theme: ThemeData(
          useMaterial3: true,
          colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff4755d6)),
          scaffoldBackgroundColor: const Color(0xfff5f6fc),
        ),
        home: const RecorderScreen(),
      );
}

class RecorderScreen extends StatelessWidget {
  const RecorderScreen({super.key});
  @override
  Widget build(BuildContext context) {
    final c = context.watch<RecorderController>();
    return Scaffold(
      appBar: AppBar(title: const Text('Voice Notes'), centerTitle: false),
      body: SafeArea(
        child: Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 680),
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Text('Capture your ideas.', style: Theme.of(context).textTheme.headlineMedium),
                const SizedBox(height: 8),
                const Text('Record audio and follow your words as they appear. Keep this screen open while recording.'),
                const SizedBox(height: 24),
                DropdownButtonFormField<String>(
                  value: c.localeId,
                  decoration: const InputDecoration(labelText: 'Speech language', border: OutlineInputBorder()),
                  items: const [
                    DropdownMenuItem(value: 'en-IN', child: Text('English · India')),
                    DropdownMenuItem(value: 'ml-IN', child: Text('Malayalam · India')),
                    DropdownMenuItem(value: 'hi-IN', child: Text('Hindi · India')),
                  ],
                  onChanged: c.canStart ? (value) { if (value != null) c.changeLocale(value); } : null,
                ),
                const SizedBox(height: 8),
                const Text('Language availability depends on your speech service.'),
                const SizedBox(height: 20),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                      Row(children: [
                        Icon(c.isRecording ? Icons.mic : Icons.mic_none),
                        const SizedBox(width: 8),
                        Text(c.state.toUpperCase()),
                        const Spacer(),
                        if (c.busy) const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)),
                      ]),
                      const SizedBox(height: 16),
                      ValueListenableBuilder<double>(
                        valueListenable: c.level,
                        builder: (_, value, __) => LinearProgressIndicator(value: value, minHeight: 8, semanticsLabel: 'Microphone level'),
                      ),
                      const SizedBox(height: 20),
                      Wrap(spacing: 12, runSpacing: 12, children: [
                        FilledButton.icon(onPressed: c.canStart ? c.start : null, icon: const Icon(Icons.mic), label: const Text('Start recording')),
                        OutlinedButton.icon(onPressed: c.canStop ? c.stop : null, icon: const Icon(Icons.stop), label: const Text('Stop & save')),
                      ]),
                    ]),
                  ),
                ),
                if (c.message != null) ...[
                  const SizedBox(height: 16),
                  Semantics(liveRegion: true, child: Text(c.message!, style: TextStyle(color: Theme.of(context).colorScheme.error))),
                ],
                const SizedBox(height: 24),
                Row(children: [
                  Text('Transcript', style: Theme.of(context).textTheme.titleLarge),
                  const Spacer(),
                  IconButton(
                    tooltip: 'Copy transcript',
                    onPressed: c.transcript.isEmpty ? null : () async {
                      await Clipboard.setData(ClipboardData(text: c.transcript));
                      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Transcript copied')));
                    },
                    icon: const Icon(Icons.copy_outlined),
                  ),
                ]),
                const SizedBox(height: 8),
                Card(child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 160),
                    child: SelectableText(c.transcript.isEmpty ? 'Your words will appear here…' : c.transcript, style: Theme.of(context).textTheme.bodyLarge),
                  ),
                )),
                if (c.result case final result?) ...[
                  const SizedBox(height: 20),
                  Text('Saved ${(result.duration.inMilliseconds / 1000).toStringAsFixed(1)} seconds of audio.', style: Theme.of(context).textTheme.titleMedium),
                  if (!result.audioComplete) const Text('Recording was interrupted. The file may contain only part of the audio.'),
                  if (!result.speechComplete) const Text('Transcription did not finish normally. The transcript may be incomplete.'),
                  const SizedBox(height: 8),
                  SelectableText(result.audioPath, style: Theme.of(context).textTheme.bodySmall),
                ],
                const SizedBox(height: 24),
                const Text('Your device’s speech service may process audio online. This starter does not guarantee offline recognition.'),
              ]),
            ),
          ),
        ),
      ),
    );
  }
}
