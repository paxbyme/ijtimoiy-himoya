import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:firebase_auth/firebase_auth.dart' as fb;
import '../config/api_config.dart';
import '../models/user_model.dart';
import '../models/task_model.dart';
import '../models/kpi_model.dart';
import '../models/ai_rule_model.dart';
import '../models/ai_conversation_model.dart';
import '../models/conversation_model.dart';

class ApiService {
  late final Dio _dio;

  ApiService() {
    _dio = Dio(BaseOptions(
      baseUrl: ApiConfig.baseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 30),
      headers: {
        'Content-Type': 'application/json',
      },
    ));

    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final user = fb.FirebaseAuth.instance.currentUser;
        if (user != null) {
          final token = await user.getIdToken();
          options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
      onError: (error, handler) {
        handler.next(error);
      },
    ));
  }

  // ---- User / Profile ----

  Future<User> getUserProfile() async {
    final response = await _dio.get('/users/me');
    return User.fromJson(response.data['data'] ?? response.data);
  }

  Future<List<User>> getStaffList() async {
    final response = await _dio.get('/users/staff');
    final data = response.data['data'] ?? response.data;
    final List<dynamic> list = data is Map ? (data['content'] ?? []) : data;
    return list.map((e) => User.fromJson(e)).toList();
  }

  Future<User> createStaff(Map<String, dynamic> data) async {
    final response = await _dio.post('/users/staff', data: data);
    return User.fromJson(response.data['data'] ?? response.data);
  }

  // ---- Tasks ----

  Future<List<Task>> getTasks() async {
    final response = await _dio.get('/tasks');
    final data = response.data['data'] ?? response.data;
    final List<dynamic> list = data is Map ? (data['content'] ?? []) : data;
    return list.map((e) => Task.fromJson(e)).toList();
  }

  Future<List<Task>> getMyTasks() async {
    final response = await _dio.get('/tasks');
    final data = response.data['data'] ?? response.data;
    final List<dynamic> list = data is Map ? (data['content'] ?? []) : data;
    return list.map((e) => Task.fromJson(e)).toList();
  }

  Future<Task> createTask(Map<String, dynamic> data) async {
    final response = await _dio.post('/tasks', data: data);
    return Task.fromJson(response.data['data'] ?? response.data);
  }

  Future<Task> completeTask(String taskId) async {
    final response = await _dio.put('/tasks/$taskId/complete');
    return Task.fromJson(response.data['data'] ?? response.data);
  }

  // ---- KPI ----

  Future<KpiScore?> getMyKpi() async {
    final response = await _dio.get('/kpi/me');
    final data = response.data['data'];
    if (data == null) return null;
    return KpiScore.fromJson(data);
  }

  Future<List<KpiScore>> getKpiRankings() async {
    final response = await _dio.get('/kpi/rankings');
    final List<dynamic> list = response.data['data'] ?? response.data;
    return list.map((e) => KpiScore.fromJson(e)).toList();
  }

  // ---- AI Chat ----

  Future<Map<String, dynamic>> sendAiMessage(
      String message, String? conversationId) async {
    final response = await _dio.post('/ai/chat', data: {
      'message': message,
      'conversationId': conversationId,
    });
    return response.data['data'] ?? response.data;
  }

  /// Stream AI chat response via SSE. Yields parsed event maps.
  Stream<Map<String, dynamic>> sendAiMessageStream(
      String message, String? conversationId) async* {
    final response = await _dio.post<ResponseBody>(
      '/ai/chat/stream',
      data: {
        'message': message,
        'conversationId': conversationId,
      },
      options: Options(responseType: ResponseType.stream),
    );

    final stream = response.data!.stream;
    String buffer = '';

    await for (final bytes in stream) {
      buffer += utf8.decode(bytes);
      final lines = buffer.split('\n');
      // Keep the last (possibly incomplete) line in the buffer
      buffer = lines.removeLast();

      for (final line in lines) {
        if (!line.startsWith('data:')) continue;
        final raw = line.substring(5).trim();
        if (raw.isEmpty) continue;
        try {
          yield json.decode(raw) as Map<String, dynamic>;
        } catch (_) {
          // Skip malformed JSON
        }
      }
    }

    // Process remaining buffer
    if (buffer.startsWith('data:')) {
      final raw = buffer.substring(5).trim();
      if (raw.isNotEmpty) {
        try {
          yield json.decode(raw) as Map<String, dynamic>;
        } catch (_) {}
      }
    }
  }

  // ---- AI Conversations ----

  Future<List<AiConversation>> getAiConversations() async {
    final response = await _dio.get('/ai/conversations');
    final List<dynamic> list = response.data['data'] ?? response.data;
    return list.map((e) => AiConversation.fromJson(e)).toList();
  }

  Future<Map<String, dynamic>> getAiConversation(String id) async {
    final response = await _dio.get('/ai/conversations/$id');
    return response.data['data'] ?? response.data;
  }

  Future<void> deleteAiConversation(String id) async {
    await _dio.delete('/ai/conversations/$id');
  }

  // ---- AI Feedback ----

  Future<void> submitAiFeedback({
    required String conversationId,
    required int messageIndex,
    required String rating,
    String? comment,
  }) async {
    await _dio.post('/ai/feedback', data: {
      'conversationId': conversationId,
      'messageIndex': messageIndex,
      'rating': rating,
      'comment': comment,
    });
  }

  // ---- AI Rules ----

  Future<List<AiRule>> getAiRules() async {
    final response = await _dio.get('/ai-rules');
    final List<dynamic> list = response.data['data'] ?? response.data;
    return list.map((e) => AiRule.fromJson(e)).toList();
  }

  Future<AiRule> createAiRule(Map<String, dynamic> data) async {
    final response = await _dio.post('/ai-rules', data: data);
    return AiRule.fromJson(response.data['data'] ?? response.data);
  }

  Future<AiRule> updateAiRule(String id, Map<String, dynamic> data) async {
    final response = await _dio.put('/ai-rules/$id', data: data);
    return AiRule.fromJson(response.data['data'] ?? response.data);
  }

  Future<void> deleteAiRule(String id) async {
    await _dio.delete('/ai-rules/$id');
  }

  // ---- Chat ----

  Future<void> sendChatMessage(String receiverId, String content) async {
    await _dio.post('/chat/send', data: {
      'receiverId': receiverId,
      'content': content,
    });
  }

  Future<List<Conversation>> getConversations() async {
    final response = await _dio.get('/chat/conversations');
    final List<dynamic> list = response.data['data'] ?? response.data;
    return list.map((e) => Conversation.fromJson(e)).toList();
  }
}
