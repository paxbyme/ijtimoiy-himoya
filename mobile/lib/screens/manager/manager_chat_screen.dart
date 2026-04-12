import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../providers/auth_provider.dart';
import '../../providers/chat_provider.dart';
import '../../widgets/chat_bubble.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';
import 'employee_list_screen.dart' show staffListProvider;

class ManagerChatScreen extends ConsumerStatefulWidget {
  final String staffId;

  const ManagerChatScreen({super.key, required this.staffId});

  @override
  ConsumerState<ManagerChatScreen> createState() => _ManagerChatScreenState();
}

class _ManagerChatScreenState extends ConsumerState<ManagerChatScreen> {
  final _messageController = TextEditingController();
  final _scrollController = ScrollController();

  @override
  void dispose() {
    _messageController.dispose();
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

  String _getConversationId(String myId) {
    final ids = [myId, widget.staffId]..sort();
    return '${ids[0]}_${ids[1]}';
  }

  void _sendMessage(String senderId) {
    final text = _messageController.text.trim();
    if (text.isEmpty) return;

    _messageController.clear();
    ref.read(apiServiceProvider).sendChatMessage(widget.staffId, text);
    _scrollToBottom();
  }

  @override
  Widget build(BuildContext context) {
    final userProfile = ref.watch(userProfileProvider);
    final theme = Theme.of(context);

    final staffAsync = ref.watch(staffListProvider);
    final staffName = staffAsync.maybeWhen(
      data: (list) {
        for (final s in list) {
          if (s.id == widget.staffId) return s.displayName;
        }
        return null;
      },
      orElse: () => null,
    ) ?? widget.staffId;

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) context.pop();
      },
      child: Scaffold(
      appBar: AppBar(
        leading: BackButton(onPressed: () => context.pop()),
        title: Text(staffName),
      ),
      body: userProfile.when(
        loading: () => const LoadingWidget(),
        error: (_, __) => const Center(child: Text('Profilni yuklashda xatolik')),
        data: (user) {
          if (user == null) {
            return const Center(child: Text('Tizimga kirilmagan'));
          }

          final conversationId = _getConversationId(user.id);
          final messagesAsync = ref.watch(messagesProvider(conversationId));

          return Column(
            children: [
              Expanded(
                child: messagesAsync.when(
                  loading: () => const LoadingWidget(),
                  error: (_, __) =>
                      const Center(child: Text('Xabarlarni yuklashda xatolik')),
                  data: (messages) {
                    WidgetsBinding.instance
                        .addPostFrameCallback((_) => _scrollToBottom());

                    if (messages.isEmpty) {
                      return const EmptyStateWidget(
                        icon: Icons.chat_bubble_outline,
                        message: 'Hali xabarlar yo\'q. Suhbatni boshlang!',
                      );
                    }

                    return ListView.builder(
                      controller: _scrollController,
                      padding: const EdgeInsets.symmetric(
                          horizontal: 16, vertical: 8),
                      itemCount: messages.length,
                      itemBuilder: (context, index) {
                        final msg = messages[index];
                        return ChatBubble(
                          message: msg.content,
                          isMe: msg.senderId == user.id,
                          timestamp: msg.createdAt,
                        );
                      },
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
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _messageController,
                          textInputAction: TextInputAction.send,
                          onSubmitted: (_) => _sendMessage(user.id),
                          decoration: InputDecoration(
                            hintText: 'Xabar yozing...',
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
                      IconButton.filled(
                        onPressed: () => _sendMessage(user.id),
                        icon: const Icon(Icons.send),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          );
        },
      ),
      ),
    );
  }
}
