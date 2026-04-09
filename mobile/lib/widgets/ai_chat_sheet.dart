import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/ai_provider.dart';
import '../providers/auth_provider.dart';
import 'chat_bubble.dart';

/// Opens the AI chat as a draggable bottom sheet.
void showAiChatSheet(BuildContext context, {String? initialMessage}) {
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: Colors.transparent,
    builder: (_) => AiChatSheet(initialMessage: initialMessage),
  );
}

class AiChatSheet extends ConsumerStatefulWidget {
  final String? initialMessage;

  const AiChatSheet({super.key, this.initialMessage});

  @override
  ConsumerState<AiChatSheet> createState() => _AiChatSheetState();
}

class _AiChatSheetState extends ConsumerState<AiChatSheet> {
  final _controller = TextEditingController();
  final _scrollController = ScrollController();
  final Set<int> _feedbackGiven = {};

  @override
  void initState() {
    super.initState();
    if (widget.initialMessage != null && widget.initialMessage!.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        ref.read(aiChatProvider.notifier).sendMessage(widget.initialMessage!);
      });
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    _scrollController.dispose();
    super.dispose();
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

  void _send() {
    final text = _controller.text.trim();
    if (text.isEmpty) return;
    ref.read(aiChatProvider.notifier).sendMessage(text);
    _controller.clear();
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
      setState(() => _feedbackGiven.add(messageIndex));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Thanks for your feedback!'),
            duration: Duration(seconds: 1),
          ),
        );
      }
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final messages = ref.watch(aiChatProvider);
    final isLoading = ref.read(aiChatProvider.notifier).isLoading;
    final theme = Theme.of(context);

    WidgetsBinding.instance.addPostFrameCallback((_) => _scrollToBottom());

    return DraggableScrollableSheet(
      initialChildSize: 0.85,
      minChildSize: 0.5,
      maxChildSize: 1.0,
      expand: false,
      builder: (context, _) => Container(
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: Column(
          children: [
            // Handle + header
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 8, 0),
              child: Column(
                children: [
                  Container(
                    width: 40,
                    height: 4,
                    margin: const EdgeInsets.only(bottom: 12),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.outline.withValues(alpha: 0.3),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 18,
                        backgroundColor: theme.colorScheme.primaryContainer,
                        child: Icon(Icons.smart_toy,
                            size: 20, color: theme.colorScheme.primary),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('AI Assistant',
                                style: theme.textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                )),
                            Text('Connected to company knowledge base',
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                )),
                          ],
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.add_comment_outlined),
                        tooltip: 'New conversation',
                        onPressed: () {
                          ref.read(aiChatProvider.notifier).clearChat();
                          setState(() => _feedbackGiven.clear());
                        },
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const Divider(height: 16),

            // Messages
            Expanded(
              child: messages.isEmpty
                  ? _buildEmptyState(theme)
                  : ListView.builder(
                      controller: _scrollController,
                      padding: const EdgeInsets.symmetric(
                          horizontal: 16, vertical: 8),
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

            // Input bar
            Container(
              padding: const EdgeInsets.fromLTRB(16, 8, 8, 12),
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
                top: false,
                child: Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _controller,
                        textInputAction: TextInputAction.send,
                        onSubmitted: (_) => _send(),
                        decoration: InputDecoration(
                          hintText: 'Ask about staff, roles, departments...',
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(24),
                            borderSide: BorderSide.none,
                          ),
                          filled: true,
                          fillColor: theme.colorScheme.surfaceContainerHighest,
                          contentPadding: const EdgeInsets.symmetric(
                              horizontal: 20, vertical: 10),
                        ),
                        maxLines: null,
                      ),
                    ),
                    const SizedBox(width: 8),
                    IconButton.filled(
                      onPressed: _send,
                      icon: const Icon(Icons.send),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyState(ThemeData theme) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.smart_toy_outlined,
              size: 56,
              color: theme.colorScheme.primary.withValues(alpha: 0.5)),
          const SizedBox(height: 16),
          Text('What can I help you with?',
              style: theme.textTheme.titleMedium
                  ?.copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          Text(
            'Ask me about staff roles, contact info, department structures, or company policies.',
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 24),
          // Suggestion chips
          Wrap(
            spacing: 8,
            runSpacing: 8,
            alignment: WrapAlignment.center,
            children: [
              _SuggestionChip(
                label: 'Who is my manager?',
                onTap: () => _sendSuggestion('Who is my manager?'),
              ),
              _SuggestionChip(
                label: 'What are my current tasks?',
                onTap: () => _sendSuggestion('What are my current tasks?'),
              ),
              _SuggestionChip(
                label: 'Show department structure',
                onTap: () => _sendSuggestion(
                    'Can you explain our department structure?'),
              ),
              _SuggestionChip(
                label: 'Team contact info',
                onTap: () =>
                    _sendSuggestion('Who can I contact on my team?'),
              ),
            ],
          ),
        ],
      ),
    );
  }

  void _sendSuggestion(String text) {
    ref.read(aiChatProvider.notifier).sendMessage(text);
    _scrollToBottom();
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
            Text('Thinking...',
                style:
                    TextStyle(color: theme.colorScheme.onSurfaceVariant)),
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
              child: Icon(Icons.thumb_up_outlined,
                  size: 16,
                  color: hasGiven
                      ? theme.colorScheme.outline
                      : theme.colorScheme.onSurfaceVariant),
            ),
          ),
          const SizedBox(width: 4),
          InkWell(
            onTap:
                hasGiven ? null : () => _submitFeedback(messageIndex, 'down'),
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.all(4),
              child: Icon(Icons.thumb_down_outlined,
                  size: 16,
                  color: hasGiven
                      ? theme.colorScheme.outline
                      : theme.colorScheme.onSurfaceVariant),
            ),
          ),
        ],
      ),
    );
  }
}

class _SuggestionChip extends StatelessWidget {
  final String label;
  final VoidCallback onTap;

  const _SuggestionChip({required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ActionChip(
      label: Text(label, style: const TextStyle(fontSize: 13)),
      onPressed: onTap,
      backgroundColor: theme.colorScheme.secondaryContainer,
      labelStyle:
          TextStyle(color: theme.colorScheme.onSecondaryContainer),
      side: BorderSide.none,
    );
  }
}
