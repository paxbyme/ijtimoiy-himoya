import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';
import '../../core/network/network_info.dart';
import '../../models/chat/ai_conversation_model.dart';
import '../../models/chat/ai_rule_model.dart';
import '../datasources/remote/ai_remote_datasource.dart';

class AiRepository {
  final AiRemoteDataSource _remote;
  final NetworkInfo _network;

  AiRepository(this._remote, this._network);

  // ---- Chat ----

  Future<Either<Failure, Map<String, dynamic>>> sendMessage(
          String message, String? conversationId) =>
      _guard(() => _remote.sendMessage(message, conversationId));

  /// SSE stream — passthrough. Network errors surface via Stream.error so
  /// the consumer's existing `try/await for` keeps working. The fallback
  /// to the non-streaming endpoint is owned by the notifier above.
  Stream<Map<String, dynamic>> sendMessageStream(
          String message, String? conversationId) =>
      _remote.sendMessageStream(message, conversationId);

  // ---- Voice ----

  Future<Either<Failure, String>> transcribeAudio(String filePath,
          {String mimeType = 'audio/m4a'}) =>
      _guard(() => _remote.transcribeAudio(filePath, mimeType: mimeType));

  // ---- Conversations ----

  Future<Either<Failure, List<AiConversation>>> getConversations() =>
      _guard(_remote.getConversations);

  Future<Either<Failure, Map<String, dynamic>>> getConversation(String id) =>
      _guard(() => _remote.getConversation(id));

  Future<Either<Failure, void>> deleteConversation(String id) =>
      _guard(() => _remote.deleteConversation(id));

  // ---- Feedback ----

  Future<Either<Failure, void>> submitFeedback({
    required String conversationId,
    required int messageIndex,
    required String rating,
    String? comment,
  }) =>
      _guard(() => _remote.submitFeedback(
            conversationId: conversationId,
            messageIndex: messageIndex,
            rating: rating,
            comment: comment,
          ));

  // ---- Rules ----

  Future<Either<Failure, List<AiRule>>> getRules() => _guard(_remote.getRules);

  Future<Either<Failure, AiRule>> createRule(Map<String, dynamic> data) =>
      _guard(() => _remote.createRule(data));

  Future<Either<Failure, AiRule>> updateRule(
          String id, Map<String, dynamic> data) =>
      _guard(() => _remote.updateRule(id, data));

  Future<Either<Failure, void>> deleteRule(String id) =>
      _guard(() => _remote.deleteRule(id));

  Future<Either<Failure, AiRule>> uploadRuleFromFile(
          String filePath, String fileName,
          {String? title, String? category}) =>
      _guard(() => _remote.uploadRuleFromFile(filePath, fileName,
          title: title, category: category));

  Future<Either<Failure, T>> _guard<T>(Future<T> Function() op) async {
    if (!await _network.isConnected) return const Left(NetworkFailure());
    try {
      return Right(await op());
    } catch (e) {
      return Left(FailureMapper.fromException(e));
    }
  }
}
