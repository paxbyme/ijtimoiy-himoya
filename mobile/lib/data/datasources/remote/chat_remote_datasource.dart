import 'package:dio/dio.dart';

import '../../../models/chat/conversation_model.dart';

/// REST chat endpoints (paginated history, server-side send). Real-time
/// streams come from the Firestore-backed local datasource.
class ChatRemoteDataSource {
  final Dio _dio;
  ChatRemoteDataSource(this._dio);

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
