import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/chat/ai_rule_model.dart';
import '../models/chat/ai_conversation_model.dart';
import 'auth_provider.dart';

final aiRulesProvider = FutureProvider<List<AiRule>>((ref) async {
  final api = ref.read(apiServiceProvider);
  return api.getAiRules();
});

// ---- AI Conversations List ----

final aiConversationsProvider = FutureProvider<List<AiConversation>>((ref) async {
  final api = ref.read(apiServiceProvider);
  return api.getAiConversations();
});

// ---- AI Chat ----

class AiChatMessage {
  final String content;
  final bool isUser;
  final DateTime timestamp;
  final int? messageIndex;

  AiChatMessage({
    required this.content,
    required this.isUser,
    DateTime? timestamp,
    this.messageIndex,
  }) : timestamp = timestamp ?? DateTime.now();

  AiChatMessage copyWith({String? content, int? messageIndex}) {
    return AiChatMessage(
      content: content ?? this.content,
      isUser: isUser,
      timestamp: timestamp,
      messageIndex: messageIndex ?? this.messageIndex,
    );
  }
}

class AiChatNotifier extends Notifier<List<AiChatMessage>> {
  String? _conversationId;
  bool _isLoading = false;

  @override
  List<AiChatMessage> build() => [];

  bool get isLoading => _isLoading;
  String? get conversationId => _conversationId;

  /// Send message with streaming response
  Future<void> sendMessage(String message) async {
    state = [
      ...state,
      AiChatMessage(content: message, isUser: true),
    ];

    _isLoading = true;
    state = [...state]; // trigger rebuild to show loading

    try {
      final api = ref.read(apiServiceProvider);
      final stream = api.sendAiMessageStream(message, _conversationId);

      String fullResponse = '';
      bool addedAiMessage = false;

      await for (final event in stream) {
        final type = event['type'] as String?;

        if (type == 'meta') {
          _conversationId = event['conversationId']?.toString();
        } else if (type == 'token') {
          final text = event['text'] as String? ?? '';
          fullResponse += text;

          if (!addedAiMessage) {
            addedAiMessage = true;
            state = [
              ...state,
              AiChatMessage(content: fullResponse, isUser: false),
            ];
          } else {
            final messages = [...state];
            messages[messages.length - 1] = AiChatMessage(
              content: fullResponse,
              isUser: false,
            );
            state = messages;
          }
        } else if (type == 'error') {
          throw Exception(event['message'] ?? 'Unknown error');
        }
      }

      _isLoading = false;
      // Assign message indices for feedback
      final messages = <AiChatMessage>[];
      for (int i = 0; i < state.length; i++) {
        messages.add(state[i].copyWith(messageIndex: i));
      }
      state = messages;

      // Refresh conversation list
      ref.invalidate(aiConversationsProvider);
    } catch (e) {
      _isLoading = false;
      // Fallback to non-streaming
      try {
        final api = ref.read(apiServiceProvider);
        final response = await api.sendAiMessage(message, _conversationId);
        _conversationId = response['conversationId']?.toString() ?? _conversationId;
        final reply = response['response'] ?? response['reply'] ?? response['message'] ?? '';

        state = [
          ...state,
          AiChatMessage(content: reply.toString(), isUser: false),
        ];
        ref.invalidate(aiConversationsProvider);
      } catch (_) {
        state = [
          ...state,
          AiChatMessage(
            content: 'Sorry, something went wrong. Please try again.',
            isUser: false,
          ),
        ];
      }
    }
  }

  /// Load an existing conversation
  Future<void> loadConversation(String id) async {
    try {
      final api = ref.read(apiServiceProvider);
      final convo = await api.getAiConversation(id);
      _conversationId = id;

      final messages = <AiChatMessage>[];
      final List<dynamic> stored = convo['messages'] ?? [];
      for (int i = 0; i < stored.length; i++) {
        final m = stored[i] as Map<String, dynamic>;
        final parts = m['parts'] as List<dynamic>?;
        final text = parts?.isNotEmpty == true
            ? (parts!.first as Map<String, dynamic>)['text']?.toString() ?? ''
            : '';
        messages.add(AiChatMessage(
          content: text,
          isUser: m['role'] == 'user',
          messageIndex: i,
        ));
      }
      state = messages;
    } catch (_) {
      // Failed to load
    }
  }

  void clearChat() {
    _conversationId = null;
    state = [];
  }
}

final aiChatProvider =
    NotifierProvider<AiChatNotifier, List<AiChatMessage>>(AiChatNotifier.new);

// ---- AI Rules Notifier ----

class AiRulesNotifier extends Notifier<AsyncValue<void>> {
  @override
  AsyncValue<void> build() => const AsyncValue.data(null);

  Future<bool> createRule(Map<String, dynamic> data) async {
    state = const AsyncValue.loading();
    try {
      await ref.read(apiServiceProvider).createAiRule(data);
      ref.invalidate(aiRulesProvider);
      state = const AsyncValue.data(null);
      return true;
    } catch (e, st) {
      state = AsyncValue.error(e, st);
      return false;
    }
  }

  Future<bool> updateRule(String id, Map<String, dynamic> data) async {
    state = const AsyncValue.loading();
    try {
      await ref.read(apiServiceProvider).updateAiRule(id, data);
      ref.invalidate(aiRulesProvider);
      state = const AsyncValue.data(null);
      return true;
    } catch (e, st) {
      state = AsyncValue.error(e, st);
      return false;
    }
  }

  Future<bool> deleteRule(String id) async {
    state = const AsyncValue.loading();
    try {
      await ref.read(apiServiceProvider).deleteAiRule(id);
      ref.invalidate(aiRulesProvider);
      state = const AsyncValue.data(null);
      return true;
    } catch (e, st) {
      state = AsyncValue.error(e, st);
      return false;
    }
  }
}

final aiRulesNotifierProvider =
    NotifierProvider<AiRulesNotifier, AsyncValue<void>>(AiRulesNotifier.new);
