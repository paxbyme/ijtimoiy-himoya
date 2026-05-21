import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';
import '../../core/network/network_info.dart';
import '../../models/auth/user_model.dart';
import '../../models/chat/chat_message_model.dart';
import '../../models/chat/conversation_model.dart';
import '../datasources/local/chat_local_datasource.dart';
import '../datasources/remote/chat_remote_datasource.dart';

/// Combines the Firestore-backed real-time chat layer with the REST
/// fallback endpoints. Streams are passthroughs (StreamProvider handles
/// their errors); writes use the failure-mapped path.
class ChatRepository {
  final ChatLocalDataSource _local;
  final ChatRemoteDataSource _remote;
  final NetworkInfo _network;

  ChatRepository(this._local, this._remote, this._network);

  // ---- Real-time streams (passthrough) ----

  Stream<List<User>> staffStream(String departmentId) =>
      _local.staffStream(departmentId);

  Stream<List<ChatMessage>> messagesStream(String conversationId) =>
      _local.messagesStream(conversationId);

  Stream<List<Conversation>> conversationsStream(String userId) =>
      _local.conversationsStream(userId);

  // ---- Writes ----

  Future<Either<Failure, void>> sendMessage({
    required String conversationId,
    required String senderId,
    required String receiverId,
    required String content,
  }) async {
    try {
      await _local.sendMessage(
        conversationId: conversationId,
        senderId: senderId,
        receiverId: receiverId,
        content: content,
      );
      return const Right(null);
    } catch (e) {
      return Left(FailureMapper.fromException(e));
    }
  }

  Future<Either<Failure, void>> resetUnreadCount(
      String conversationId, String userId) async {
    try {
      await _local.resetUnreadCount(conversationId, userId);
      return const Right(null);
    } catch (e) {
      return Left(FailureMapper.fromException(e));
    }
  }

  Future<Either<Failure, void>> markMessageRead(String messageId) async {
    try {
      await _local.markMessageRead(messageId);
      return const Right(null);
    } catch (e) {
      return Left(FailureMapper.fromException(e));
    }
  }

  // ---- REST fallback ----

  Future<Either<Failure, List<Conversation>>> getConversationsRest() =>
      _restGuard(_remote.getConversations);

  Future<Either<Failure, void>> sendMessageRest(
          String receiverId, String content) =>
      _restGuard(() => _remote.sendChatMessage(receiverId, content));

  Future<Either<Failure, T>> _restGuard<T>(Future<T> Function() op) async {
    if (!await _network.isConnected) return const Left(NetworkFailure());
    try {
      return Right(await op());
    } catch (e) {
      return Left(FailureMapper.fromException(e));
    }
  }
}
