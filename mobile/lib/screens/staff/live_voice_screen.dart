import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'dart:typed_data';

import 'package:firebase_auth/firebase_auth.dart' as fb;
import 'package:flutter/material.dart';
import 'package:flutter_pcm_sound/flutter_pcm_sound.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:record/record.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../../config/api_config.dart';

enum _LiveStatus { idle, connecting, listening, speaking, error }

class LiveVoiceScreen extends ConsumerStatefulWidget {
  const LiveVoiceScreen({super.key});

  @override
  ConsumerState<LiveVoiceScreen> createState() => _LiveVoiceScreenState();
}

class _LiveVoiceScreenState extends ConsumerState<LiveVoiceScreen>
    with SingleTickerProviderStateMixin {
  static const int _micSampleRate = 16000;
  static const int _speakerSampleRate = 24000;

  final AudioRecorder _recorder = AudioRecorder();
  WebSocketChannel? _channel;
  StreamSubscription<Uint8List>? _micSub;

  final Queue<int> _playbackQueue = Queue<int>();
  bool _pcmSoundReady = false;
  Timer? _silenceTimer;

  _LiveStatus _status = _LiveStatus.idle;
  String _errorMessage = '';
  bool _userSpeaking = false;

  late final AnimationController _pulse;

  @override
  void initState() {
    super.initState();
    _pulse = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _pulse.dispose();
    _disconnect();
    super.dispose();
  }

  // ---- Lifecycle ----

  Future<void> _start() async {
    if (_status != _LiveStatus.idle && _status != _LiveStatus.error) return;
    setState(() {
      _status = _LiveStatus.connecting;
      _errorMessage = '';
    });

    try {
      if (!await _recorder.hasPermission()) {
        _setError('Mikrofon ruxsati berilmagan');
        return;
      }

      final token = await fb.FirebaseAuth.instance.currentUser?.getIdToken();
      if (token == null) {
        _setError('Avval tizimga kiring');
        return;
      }

      await _setupPlayback();

      final uri = Uri.parse('${ApiConfig.wsBaseUrl}/ai/live');
      debugPrint('[Live] connecting to $uri');
      _channel = WebSocketChannel.connect(uri);
      _channel!.stream.listen(
        _onWsMessage,
        onError: (e) {
          debugPrint('[Live] WS error: $e');
          _setError('Ulanish xatosi: $e');
        },
        onDone: () {
          debugPrint('[Live] WS done — closeCode=${_channel?.closeCode} reason=${_channel?.closeReason}');
          if (mounted && _status != _LiveStatus.idle) {
            _setError('Aloqa uzildi (code=${_channel?.closeCode})');
          }
        },
      );

      debugPrint('[Live] sending token');
      _channel!.sink.add(jsonEncode({'token': token}));
    } catch (e) {
      _setError('Boshlashda xatolik: $e');
    }
  }

  Future<void> _setupPlayback() async {
    if (_pcmSoundReady) return;
    await FlutterPcmSound.setup(
      sampleRate: _speakerSampleRate,
      channelCount: 1,
    );
    await FlutterPcmSound.setFeedThreshold(_speakerSampleRate); // ~1 sec
    FlutterPcmSound.setFeedCallback(_onFeed);
    FlutterPcmSound.start();
    _pcmSoundReady = true;
  }

  void _onFeed(int remainingFrames) {
    if (_playbackQueue.isEmpty) return;
    // Drain up to 0.5s of audio per callback to keep latency low
    final maxFrames = _speakerSampleRate ~/ 2;
    final n = _playbackQueue.length < maxFrames ? _playbackQueue.length : maxFrames;
    final samples = List<int>.generate(n, (_) => _playbackQueue.removeFirst());
    FlutterPcmSound.feed(PcmArrayInt16.fromList(samples));
  }

  Future<void> _onWsMessage(dynamic msg) async {
    if (msg is String) {
      debugPrint('[Live] <-- text: $msg');
      try {
        final json = jsonDecode(msg) as Map<String, dynamic>;
        final event = json['event'];
        if (event == 'ready') {
          await _startMic();
          if (mounted) setState(() => _status = _LiveStatus.listening);
        } else if (event == 'turnComplete') {
          if (mounted) setState(() => _status = _LiveStatus.listening);
        } else if (event == 'error') {
          _setError('Server xatosi: ${json['message'] ?? 'unknown'}');
        }
      } catch (e) {
        debugPrint('[Live] failed to parse text: $e');
      }
    } else if (msg is List<int>) {
      debugPrint('[Live] <-- binary ${msg.length}B');
      final bytes = Uint8List.fromList(msg);
      final samples = bytes.buffer.asInt16List(
        bytes.offsetInBytes,
        bytes.lengthInBytes ~/ 2,
      );
      for (final s in samples) {
        _playbackQueue.add(s);
      }
      if (mounted && _status != _LiveStatus.speaking) {
        setState(() => _status = _LiveStatus.speaking);
      }
    }
  }

  Future<void> _startMic() async {
    final stream = await _recorder.startStream(
      const RecordConfig(
        encoder: AudioEncoder.pcm16bits,
        sampleRate: _micSampleRate,
        numChannels: 1,
        echoCancel: true,
        noiseSuppress: true,
      ),
    );
    int chunkCount = 0;
    _micSub = stream.listen(
      (chunk) {
        chunkCount++;
        if (chunkCount % 25 == 1) {
          debugPrint('[Live] --> mic chunk #$chunkCount (${chunk.length}B)');
        }
        _channel?.sink.add(chunk);
        _detectSpeech(chunk);
      },
      onError: (e) {
        debugPrint('[Live] mic error: $e');
        _setError('Mikrofon xatosi: $e');
      },
    );
    debugPrint('[Live] mic streaming started');
  }

  /// Lightweight RMS-based VAD: when audio drops below threshold for ~700ms,
  /// signal end-of-turn so Gemini responds.
  void _detectSpeech(Uint8List chunk) {
    final samples = chunk.buffer.asInt16List(
      chunk.offsetInBytes,
      chunk.lengthInBytes ~/ 2,
    );
    if (samples.isEmpty) return;
    int sumSq = 0;
    for (final s in samples) {
      sumSq += s * s;
    }
    final rms = (sumSq / samples.length).clamp(0, double.infinity);
    final loud = rms > 1500000; // ~~ -25 dBFS

    if (loud) {
      if (!_userSpeaking) {
        debugPrint('[Live] VAD: user started speaking (rms=${rms.toStringAsFixed(0)})');
        _userSpeaking = true;
      }
      _silenceTimer?.cancel();
      _silenceTimer = null;
    } else if (_userSpeaking) {
      _silenceTimer ??= Timer(const Duration(milliseconds: 700), () {
        debugPrint('[Live] VAD: silence detected, sending endTurn');
        _userSpeaking = false;
        _channel?.sink.add(jsonEncode({'event': 'endTurn'}));
      });
    }
  }

  void _setError(String msg) {
    debugPrint('[Live] ERROR: $msg');
    if (!mounted) return;
    setState(() {
      _status = _LiveStatus.error;
      _errorMessage = msg;
    });
    _disconnect();
  }

  Future<void> _disconnect() async {
    _silenceTimer?.cancel();
    _silenceTimer = null;
    _userSpeaking = false;

    await _micSub?.cancel();
    _micSub = null;
    if (await _recorder.isRecording()) {
      await _recorder.stop();
    }

    await _channel?.sink.close();
    _channel = null;

    _playbackQueue.clear();
    if (_pcmSoundReady) {
      await FlutterPcmSound.release();
      _pcmSoundReady = false;
    }
  }

  Future<void> _stop() async {
    await _disconnect();
    if (mounted) {
      setState(() {
        _status = _LiveStatus.idle;
        _errorMessage = '';
      });
    }
  }

  // ---- UI ----

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Jonli suhbat')),
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _buildStatusLabel(theme),
                const SizedBox(height: 48),
                _buildMicOrb(theme),
                const SizedBox(height: 48),
                _buildHelperText(theme),
                if (_status == _LiveStatus.error) ...[
                  const SizedBox(height: 16),
                  Text(
                    _errorMessage,
                    style: TextStyle(color: theme.colorScheme.error),
                    textAlign: TextAlign.center,
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStatusLabel(ThemeData theme) {
    final (text, color) = switch (_status) {
      _LiveStatus.idle => ('Suhbatni boshlash uchun bosing', theme.colorScheme.onSurfaceVariant),
      _LiveStatus.connecting => ('Ulanmoqda...', theme.colorScheme.primary),
      _LiveStatus.listening => ('Tinglayapman...', theme.colorScheme.primary),
      _LiveStatus.speaking => ('AI gapirmoqda', theme.colorScheme.tertiary),
      _LiveStatus.error => ('Xatolik', theme.colorScheme.error),
    };
    return Text(
      text,
      style: theme.textTheme.titleLarge?.copyWith(color: color, fontWeight: FontWeight.w600),
    );
  }

  Widget _buildMicOrb(ThemeData theme) {
    final isActive = _status == _LiveStatus.listening || _status == _LiveStatus.speaking;
    final color = switch (_status) {
      _LiveStatus.speaking => theme.colorScheme.tertiary,
      _LiveStatus.error => theme.colorScheme.error,
      _ => theme.colorScheme.primary,
    };

    return GestureDetector(
      onTap: () {
        if (_status == _LiveStatus.idle || _status == _LiveStatus.error) {
          _start();
        } else {
          _stop();
        }
      },
      child: AnimatedBuilder(
        animation: _pulse,
        builder: (context, _) {
          final scale = isActive ? 1.0 + 0.06 * _pulse.value : 1.0;
          return Transform.scale(
            scale: scale,
            child: Container(
              width: 180,
              height: 180,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: color,
                boxShadow: [
                  BoxShadow(
                    color: color.withValues(alpha: 0.4),
                    blurRadius: isActive ? 40 : 16,
                    spreadRadius: isActive ? 8 : 0,
                  ),
                ],
              ),
              child: Icon(
                _status == _LiveStatus.idle || _status == _LiveStatus.error
                    ? Icons.mic
                    : Icons.stop,
                color: Colors.white,
                size: 72,
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildHelperText(ThemeData theme) {
    if (_status == _LiveStatus.idle) {
      return Text(
        'AI bilan jonli ravishda gaplashing.\nO\'zbek tilida tushunadi va javob qaytaradi.',
        style: theme.textTheme.bodyMedium?.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
        ),
        textAlign: TextAlign.center,
      );
    }
    if (_status == _LiveStatus.listening || _status == _LiveStatus.speaking) {
      return Text(
        'Tugatish uchun bosing',
        style: theme.textTheme.bodySmall?.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
        ),
      );
    }
    return const SizedBox.shrink();
  }
}
