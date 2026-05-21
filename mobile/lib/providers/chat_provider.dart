import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/datasources/local/chat_local_datasource.dart';
import '../data/datasources/remote/chat_remote_datasource.dart';
import '../data/repositories/chat_repository.dart';
import '../models/chat/chat_message_model.dart';
import '../models/chat/conversation_model.dart';
import 'auth_provider.dart';

final chatLocalDataSourceProvider =
    Provider<ChatLocalDataSource>((ref) => ChatLocalDataSource());

final chatRemoteDataSourceProvider = Provider<ChatRemoteDataSource>(
    (ref) => ChatRemoteDataSource(ref.read(dioProvider)));

final chatRepositoryProvider = Provider<ChatRepository>((ref) {
  return ChatRepository(
    ref.read(chatLocalDataSourceProvider),
    ref.read(chatRemoteDataSourceProvider),
    ref.read(networkInfoProvider),
  );
});

final conversationsProvider =
    StreamProvider.family<List<Conversation>, String>((ref, userId) {
  return ref.watch(chatRepositoryProvider).conversationsStream(userId);
});

final messagesProvider =
    StreamProvider.family<List<ChatMessage>, String>((ref, conversationId) {
  return ref.watch(chatRepositoryProvider).messagesStream(conversationId);
});
