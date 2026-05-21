import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/chat/chat_message_model.dart';
import '../models/chat/conversation_model.dart';
import 'auth_provider.dart';

final conversationsProvider =
    StreamProvider.family<List<Conversation>, String>((ref, userId) {
  final firestoreService = ref.watch(firestoreServiceProvider);
  return firestoreService.conversationsStream(userId);
});

final messagesProvider =
    StreamProvider.family<List<ChatMessage>, String>((ref, conversationId) {
  final firestoreService = ref.watch(firestoreServiceProvider);
  return firestoreService.messagesStream(conversationId);
});
