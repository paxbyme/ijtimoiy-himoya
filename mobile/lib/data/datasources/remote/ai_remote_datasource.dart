import 'dart:convert';

import 'package:dio/dio.dart';

import '../../../core/constants/app_constants.dart';
import '../../../models/chat/ai_conversation_model.dart';
import '../../../models/chat/ai_rule_model.dart';

class AiRemoteDataSource {
  final Dio _dio;
  AiRemoteDataSource(this._dio);

  // ---- Chat ----

  Future<Map<String, dynamic>> sendMessage(
      String message, String? conversationId) async {
    final response = await _dio.post('/ai/chat', data: {
      'message': message,
      'conversationId': conversationId,
    });
    return response.data['data'] ?? response.data;
  }

  /// Streams Server-Sent Events from `/ai/chat/stream`.
  ///
  /// Yields parsed JSON payloads from each `data:` line. Network errors
  /// surface as stream errors — callers should wrap with try/await for.
  Stream<Map<String, dynamic>> sendMessageStream(
      String message, String? conversationId) async* {
    final response = await _dio.post<ResponseBody>(
      '/ai/chat/stream',
      data: {
        'message': message,
        'conversationId': conversationId,
      },
      options: Options(
        responseType: ResponseType.stream,
        receiveTimeout: AppConstants.sseTimeout,
      ),
    );

    final stream = response.data!.stream;
    String buffer = '';

    await for (final bytes in stream) {
      buffer += utf8.decode(bytes);
      final lines = buffer.split('\n');
      buffer = lines.removeLast();

      for (final line in lines) {
        if (!line.startsWith('data:')) continue;
        final raw = line.substring(5).trim();
        if (raw.isEmpty) continue;
        try {
          yield json.decode(raw) as Map<String, dynamic>;
        } catch (_) {
          // skip malformed SSE payload
        }
      }
    }

    if (buffer.startsWith('data:')) {
      final raw = buffer.substring(5).trim();
      if (raw.isNotEmpty) {
        try {
          yield json.decode(raw) as Map<String, dynamic>;
        } catch (_) {}
      }
    }
  }

  // ---- Voice ----

  Future<String> transcribeAudio(String filePath,
      {String mimeType = 'audio/m4a'}) async {
    final formData = FormData.fromMap({
      'audio': await MultipartFile.fromFile(
        filePath,
        filename: filePath.split('/').last,
        contentType: DioMediaType.parse(mimeType),
      ),
    }, ListFormat.multiCompatible);

    final response = await _dio.post(
      '/ai/transcribe',
      data: formData,
      options: Options(
        sendTimeout: AppConstants.uploadTimeout,
        receiveTimeout: AppConstants.uploadTimeout,
      ),
    );
    final data = response.data['data'] ?? response.data;
    return (data['transcript'] ?? '').toString();
  }

  // ---- Conversations ----

  Future<List<AiConversation>> getConversations() async {
    final response = await _dio.get('/ai/conversations');
    final List<dynamic> list = response.data['data'] ?? response.data;
    return list.map((e) => AiConversation.fromJson(e)).toList();
  }

  Future<Map<String, dynamic>> getConversation(String id) async {
    final response = await _dio.get('/ai/conversations/$id');
    return response.data['data'] ?? response.data;
  }

  Future<void> deleteConversation(String id) =>
      _dio.delete('/ai/conversations/$id');

  // ---- Feedback ----

  Future<void> submitFeedback({
    required String conversationId,
    required int messageIndex,
    required String rating,
    String? comment,
  }) =>
      _dio.post('/ai/feedback', data: {
        'conversationId': conversationId,
        'messageIndex': messageIndex,
        'rating': rating,
        'comment': comment,
      });

  // ---- Rules ----

  Future<List<AiRule>> getRules() async {
    final response = await _dio.get('/ai-rules');
    final List<dynamic> list = response.data['data'] ?? response.data;
    return list.map((e) => AiRule.fromJson(e)).toList();
  }

  Future<AiRule> createRule(Map<String, dynamic> data) async {
    final response = await _dio.post('/ai-rules', data: data);
    return AiRule.fromJson(response.data['data'] ?? response.data);
  }

  Future<AiRule> updateRule(String id, Map<String, dynamic> data) async {
    final response = await _dio.put('/ai-rules/$id', data: data);
    return AiRule.fromJson(response.data['data'] ?? response.data);
  }

  Future<void> deleteRule(String id) => _dio.delete('/ai-rules/$id');

  Future<AiRule> uploadRuleFromFile(String filePath, String fileName,
      {String? title, String? category}) async {
    final formData = FormData.fromMap({
      'file': await MultipartFile.fromFile(filePath, filename: fileName),
      if (title != null) 'title': title,
      if (category != null) 'category': category,
    });
    final response = await _dio.post(
      '/ai-rules/upload',
      data: formData,
      options: Options(
        sendTimeout: AppConstants.uploadTimeout,
        receiveTimeout: AppConstants.uploadTimeout,
      ),
    );
    return AiRule.fromJson(response.data['data'] ?? response.data);
  }
}
