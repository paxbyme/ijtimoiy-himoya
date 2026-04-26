import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';
import '../../providers/ai_provider.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/chat_bubble.dart';
import '../../widgets/app_background.dart';

class AiChatbotScreen extends ConsumerStatefulWidget {
  const AiChatbotScreen({super.key});

  @override
  ConsumerState<AiChatbotScreen> createState() => _AiChatbotScreenState();
}

class _AiChatbotScreenState extends ConsumerState<AiChatbotScreen> {
  final _messageController = TextEditingController();
  final _scrollController = ScrollController();
  final Set<int> _feedbackGiven = {};

  final AudioRecorder _recorder = AudioRecorder();
  bool _isRecording = false;
  bool _isTranscribing = false;
  DateTime? _recordingStart;

  @override
  void initState() {
    super.initState();
    _messageController.addListener(_onTextChanged);
  }

  void _onTextChanged() {
    // Rebuild to swap mic ↔ send button
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _messageController.removeListener(_onTextChanged);
    _messageController.dispose();
    _scrollController.dispose();
    _recorder.dispose();
    super.dispose();
  }

  Future<void> _startRecording() async {
    if (_isRecording || _isTranscribing) return;

    try {
      if (!await _recorder.hasPermission()) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Mikrofon ruxsati berilmagan')),
          );
        }
        return;
      }

      final dir = await getTemporaryDirectory();
      final path =
          '${dir.path}/voice_${DateTime.now().millisecondsSinceEpoch}.m4a';

      await _recorder.start(
        const RecordConfig(
          encoder: AudioEncoder.aacLc,
          bitRate: 64000,
          sampleRate: 16000,
          numChannels: 1,
        ),
        path: path,
      );

      setState(() {
        _isRecording = true;
        _recordingStart = DateTime.now();
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Yozib olishda xatolik: $e')),
        );
      }
    }
  }

  Future<void> _stopAndTranscribe({bool cancel = false}) async {
    if (!_isRecording) return;

    final path = await _recorder.stop();
    final start = _recordingStart;
    setState(() {
      _isRecording = false;
      _recordingStart = null;
    });

    if (cancel || path == null) {
      return;
    }

    // Skip very short recordings (likely accidental taps)
    if (start != null && DateTime.now().difference(start).inMilliseconds < 500) {
      return;
    }

    setState(() => _isTranscribing = true);
    try {
      final transcript =
          await ref.read(apiServiceProvider).transcribeAudio(path);
      final trimmed = transcript.trim();
      if (trimmed.isNotEmpty) {
        ref.read(aiChatProvider.notifier).sendMessage(trimmed);
        _scrollToBottom();
      } else if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Ovoz tushunilmadi, qayta urinib ko\'ring')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Transkripsiyada xatolik: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _isTranscribing = false);
    }
  }

  void _scrollToBottom() {
    if (_scrollController.hasClients) {
      Future.delayed(const Duration(milliseconds: 100), () {
        if (_scrollController.hasClients) {
          _scrollController.animateTo(
            _scrollController.position.maxScrollExtent,
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeOut,
          );
        }
      });
    }
  }

  void _sendMessage() {
    final text = _messageController.text.trim();
    if (text.isEmpty) return;

    ref.read(aiChatProvider.notifier).sendMessage(text);
    _messageController.clear();
    _scrollToBottom();
  }

  Future<void> _submitFeedback(int messageIndex, String rating) async {
    final conversationId = ref.read(aiChatProvider.notifier).conversationId;
    if (conversationId == null) return;

    try {
      await ref.read(apiServiceProvider).submitAiFeedback(
            conversationId: conversationId,
            messageIndex: messageIndex,
            rating: rating,
          );
      setState(() {
        _feedbackGiven.add(messageIndex);
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Fikr-mulohazangiz uchun rahmat!'),
            duration: Duration(seconds: 1),
          ),
        );
      }
    } catch (_) {}
  }

  void _showConversationHistory() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (context) => DraggableScrollableSheet(
        initialChildSize: 0.6,
        minChildSize: 0.3,
        maxChildSize: 0.9,
        expand: false,
        builder: (context, scrollController) => _ConversationHistorySheet(
          scrollController: scrollController,
          onSelect: (id) {
            Navigator.pop(context);
            ref.read(aiChatProvider.notifier).loadConversation(id);
            setState(() {
              _feedbackGiven.clear();
            });
          },
          onDelete: (id) async {
            await ref.read(apiServiceProvider).deleteAiConversation(id);
            ref.invalidate(aiConversationsProvider);
            final currentId = ref.read(aiChatProvider.notifier).conversationId;
            if (currentId == id) {
              ref.read(aiChatProvider.notifier).clearChat();
            }
          },
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final messages = ref.watch(aiChatProvider);
    final isLoading = ref.read(aiChatProvider.notifier).isLoading;
    final theme = Theme.of(context);

    // Auto-scroll when messages change
    WidgetsBinding.instance.addPostFrameCallback((_) => _scrollToBottom());

    return Scaffold(
      appBar: AppBar(
        title: const Text('AI Yordamchi'),
        actions: [
          IconButton(
            icon: const Icon(Icons.history),
            tooltip: 'Suhbat tarixi',
            onPressed: _showConversationHistory,
          ),
          IconButton(
            icon: const Icon(Icons.add_comment_outlined),
            tooltip: 'Yangi suhbat',
            onPressed: () {
              ref.read(aiChatProvider.notifier).clearChat();
              setState(() {
                _feedbackGiven.clear();
              });
            },
          ),
        ],
      ),
      body: AppBackground(child: Column(
        children: [
          Expanded(
            child: messages.isEmpty
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(32),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Opacity(
                            opacity: 0.5,
                            child: Image.asset(
                              'assets/images/logo.png',
                              width: 80,
                              height: 80,
                            ),
                          ),
                          const SizedBox(height: 16),
                          Text(
                            'AI Yordamchi',
                            style: theme.textTheme.headlineSmall,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            'Ish, topshiriqlar yoki kompaniya qoidalari haqida savol bering.',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                            textAlign: TextAlign.center,
                          ),
                        ],
                      ),
                    ),
                  )
                : ListView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 8,
                    ),
                    itemCount: messages.length + (isLoading ? 1 : 0),
                    itemBuilder: (context, index) {
                      if (index == messages.length && isLoading) {
                        return _buildThinkingIndicator(theme);
                      }

                      final msg = messages[index];

                      return Column(
                        crossAxisAlignment: msg.isUser
                            ? CrossAxisAlignment.end
                            : CrossAxisAlignment.start,
                        children: [
                          ChatBubble(
                            message: msg.content,
                            isMe: msg.isUser,
                            timestamp: msg.timestamp,
                          ),
                          // Feedback buttons for AI messages
                          if (!msg.isUser &&
                              !isLoading &&
                              msg.content.isNotEmpty &&
                              msg.messageIndex != null)
                            _buildFeedbackRow(msg.messageIndex!, theme),
                        ],
                      );
                    },
                  ),
          ),
          Container(
            padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
            decoration: BoxDecoration(
              color: theme.colorScheme.surface,
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.05),
                  blurRadius: 8,
                  offset: const Offset(0, -2),
                ),
              ],
            ),
            child: SafeArea(
              bottom: false,
              child: _isRecording
                  ? _buildRecordingBar(theme)
                  : Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _messageController,
                            textInputAction: TextInputAction.send,
                            onSubmitted: (_) => _sendMessage(),
                            enabled: !_isTranscribing,
                            decoration: InputDecoration(
                              hintText: _isTranscribing
                                  ? 'Ovoz matnga o\'girilmoqda...'
                                  : 'Xabar yozing...',
                              border: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(24),
                                borderSide: BorderSide.none,
                              ),
                              filled: true,
                              fillColor:
                                  theme.colorScheme.surfaceContainerHighest,
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 20,
                                vertical: 10,
                              ),
                            ),
                            maxLines: null,
                          ),
                        ),
                        const SizedBox(width: 8),
                        _buildMicOrSendButton(theme),
                      ],
                    ),
            ),
          ),
        ],
      )),
    );
  }

  Widget _buildMicOrSendButton(ThemeData theme) {
    final hasText = _messageController.text.trim().isNotEmpty;

    if (_isTranscribing) {
      return const SizedBox(
        width: 48,
        height: 48,
        child: Center(
          child: SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      );
    }

    if (hasText) {
      return IconButton.filled(
        onPressed: _sendMessage,
        icon: const Icon(Icons.send),
      );
    }

    return IconButton.filled(
      onPressed: _startRecording,
      icon: const Icon(Icons.mic),
    );
  }

  Widget _buildRecordingBar(ThemeData theme) {
    return Row(
      children: [
        Container(
          width: 12,
          height: 12,
          decoration: BoxDecoration(
            color: theme.colorScheme.error,
            shape: BoxShape.circle,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            'Yozib olinmoqda...',
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
        IconButton(
          tooltip: 'Bekor qilish',
          onPressed: () => _stopAndTranscribe(cancel: true),
          icon: Icon(Icons.close, color: theme.colorScheme.error),
        ),
        const SizedBox(width: 4),
        IconButton.filled(
          tooltip: 'Yuborish',
          onPressed: () => _stopAndTranscribe(),
          icon: const Icon(Icons.send),
        ),
      ],
    );
  }

  Widget _buildThinkingIndicator(ThemeData theme) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 4),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: theme.colorScheme.primary,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              'O\'ylanmoqda...',
              style: TextStyle(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFeedbackRow(int messageIndex, ThemeData theme) {
    final hasGiven = _feedbackGiven.contains(messageIndex);

    return Padding(
      padding: const EdgeInsets.only(left: 8, bottom: 8),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          InkWell(
            onTap: hasGiven ? null : () => _submitFeedback(messageIndex, 'up'),
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.all(4),
              child: Icon(
                Icons.thumb_up_outlined,
                size: 16,
                color: hasGiven
                    ? theme.colorScheme.outline
                    : theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          const SizedBox(width: 4),
          InkWell(
            onTap:
                hasGiven ? null : () => _submitFeedback(messageIndex, 'down'),
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.all(4),
              child: Icon(
                Icons.thumb_down_outlined,
                size: 16,
                color: hasGiven
                    ? theme.colorScheme.outline
                    : theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ---- Conversation History Bottom Sheet ----

class _ConversationHistorySheet extends ConsumerWidget {
  final ScrollController scrollController;
  final Function(String id) onSelect;
  final Function(String id) onDelete;

  const _ConversationHistorySheet({
    required this.scrollController,
    required this.onSelect,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final conversationsAsync = ref.watch(aiConversationsProvider);
    final theme = Theme.of(context);

    return Container(
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: Column(
        children: [
          // Handle bar
          Container(
            width: 40,
            height: 4,
            margin: const EdgeInsets.only(top: 12, bottom: 8),
            decoration: BoxDecoration(
              color: theme.colorScheme.outline.withValues(alpha: 0.3),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                Icon(Icons.history, color: theme.colorScheme.primary),
                const SizedBox(width: 8),
                Text(
                  'Suhbat tarixi',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
          const Divider(),
          Expanded(
            child: conversationsAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(
                child: Text('Suhbatlarni yuklashda xatolik',
                    style: TextStyle(color: theme.colorScheme.error)),
              ),
              data: (conversations) {
                if (conversations.isEmpty) {
                  return Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.chat_bubble_outline,
                            size: 48,
                            color: theme.colorScheme.outline),
                        const SizedBox(height: 12),
                        Text(
                          'Hali suhbatlar yo\'q',
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  );
                }

                return ListView.separated(
                  controller: scrollController,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  itemCount: conversations.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 2),
                  itemBuilder: (context, index) {
                    final convo = conversations[index];
                    return Dismissible(
                      key: Key(convo.id),
                      direction: DismissDirection.endToStart,
                      confirmDismiss: (_) => showDialog<bool>(
                        context: context,
                        builder: (ctx) => AlertDialog(
                          title: const Text('Suhbatni o\'chirish?'),
                          content: const Text(
                              'Bu amalni ortga qaytarib bo\'lmaydi.'),
                          actions: [
                            TextButton(
                              onPressed: () => Navigator.pop(ctx, false),
                              child: const Text('Bekor'),
                            ),
                            TextButton(
                              onPressed: () => Navigator.pop(ctx, true),
                              child: const Text('O\'chirish'),
                            ),
                          ],
                        ),
                      ),
                      onDismissed: (_) => onDelete(convo.id),
                      background: Container(
                        alignment: Alignment.centerRight,
                        padding: const EdgeInsets.only(right: 16),
                        color: theme.colorScheme.error,
                        child: Icon(Icons.delete,
                            color: theme.colorScheme.onError),
                      ),
                      child: ListTile(
                        leading: CircleAvatar(
                          backgroundColor:
                              theme.colorScheme.primaryContainer,
                          child: Padding(
                            padding: const EdgeInsets.all(6),
                            child: Image.asset('assets/images/logo.png'),
                          ),
                        ),
                        title: Text(
                          convo.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                        subtitle: Text(
                          '${convo.messageCount} ta xabar',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                        trailing: Icon(Icons.chevron_right,
                            color: theme.colorScheme.outline),
                        onTap: () => onSelect(convo.id),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
