import 'package:dartz/dartz.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/error/failures.dart';
import '../data/datasources/remote/ai_remote_datasource.dart';
import '../data/repositories/ai_repository.dart';
import '../models/chat/ai_conversation_model.dart';
import '../models/chat/ai_rule_model.dart';
import 'auth_provider.dart';

// ---- DI ----

final aiRemoteDataSourceProvider = Provider<AiRemoteDataSource>(
    (ref) => AiRemoteDataSource(ref.read(dioProvider)));

final aiRepositoryProvider = Provider<AiRepository>((ref) {
  return AiRepository(
    ref.read(aiRemoteDataSourceProvider),
    ref.read(networkInfoProvider),
  );
});

// ---- Lists ----

final aiRulesProvider = FutureProvider<List<AiRule>>((ref) async {
  final result = await ref.read(aiRepositoryProvider).getRules();
  return result.fold((f) => throw f, (rules) => rules);
});

final aiConversationsProvider =
    FutureProvider<List<AiConversation>>((ref) async {
  final result = await ref.read(aiRepositoryProvider).getConversations();
  return result.fold((f) => throw f, (list) => list);
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

  AiRepository get _repo => ref.read(aiRepositoryProvider);

  /// Send message with streaming response, falling back to the non-stream
  /// endpoint when the stream fails (network blip, server error).
  Future<void> sendMessage(String message) async {
    state = [
      ...state,
      AiChatMessage(content: message, isUser: true),
    ];

    _isLoading = true;
    state = [...state];

    try {
      final stream = _repo.sendMessageStream(message, _conversationId);

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

      final messages = <AiChatMessage>[];
      for (int i = 0; i < state.length; i++) {
        messages.add(state[i].copyWith(messageIndex: i));
      }
      state = messages;

      ref.invalidate(aiConversationsProvider);
    } catch (_) {
      _isLoading = false;
      // Fallback to non-streaming endpoint
      final result = await _repo.sendMessage(message, _conversationId);
      result.fold(
        (_) {
          state = [
            ...state,
            AiChatMessage(
              content: 'Sorry, something went wrong. Please try again.',
              isUser: false,
            ),
          ];
        },
        (response) {
          _conversationId =
              response['conversationId']?.toString() ?? _conversationId;
          final reply = response['response'] ??
              response['reply'] ??
              response['message'] ??
              '';
          state = [
            ...state,
            AiChatMessage(content: reply.toString(), isUser: false),
          ];
          ref.invalidate(aiConversationsProvider);
        },
      );
    }
  }

  Future<void> loadConversation(String id) async {
    final result = await _repo.getConversation(id);
    result.fold((_) {}, (convo) {
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
    });
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

  AiRepository get _repo => ref.read(aiRepositoryProvider);

  Future<bool> _run<T>(Future<Either<Failure, T>> Function() op) async {
    state = const AsyncValue.loading();
    final result = await op();
    return result.fold(
      (failure) {
        state = AsyncValue.error(failure, StackTrace.current);
        return false;
      },
      (_) {
        ref.invalidate(aiRulesProvider);
        state = const AsyncValue.data(null);
        return true;
      },
    );
  }

  Future<bool> createRule(Map<String, dynamic> data) =>
      _run(() => _repo.createRule(data));

  Future<bool> updateRule(String id, Map<String, dynamic> data) =>
      _run(() => _repo.updateRule(id, data));

  Future<bool> deleteRule(String id) => _run(() => _repo.deleteRule(id));
}

final aiRulesNotifierProvider =
    NotifierProvider<AiRulesNotifier, AsyncValue<void>>(AiRulesNotifier.new);
