import 'package:cloud_firestore/cloud_firestore.dart';

import '../../../models/auth/user_model.dart';
import '../../../models/chat/chat_message_model.dart';
import '../../../models/chat/conversation_model.dart';

/// Cloud Firestore access for real-time chat. Streams flow up unmodified
/// — the repository exposes them directly because Riverpod's
/// `StreamProvider` already surfaces errors as `AsyncError`.
class ChatLocalDataSource {
  final FirebaseFirestore _firestore;

  ChatLocalDataSource([FirebaseFirestore? firestore])
      : _firestore = firestore ?? FirebaseFirestore.instance;

  Stream<List<User>> staffStream(String departmentId) {
    return _firestore
        .collection('users')
        .where('departmentId', isEqualTo: departmentId)
        .snapshots()
        .map((snapshot) => snapshot.docs
            .map((doc) => User.fromJson({...doc.data(), 'id': doc.id}))
            .where((u) => u.role == 'STAFF' && u.isActive)
            .toList());
  }

  Stream<List<ChatMessage>> messagesStream(String conversationId) {
    return _firestore
        .collection('messages')
        .where('conversationId', isEqualTo: conversationId)
        .orderBy('createdAt', descending: false)
        .snapshots()
        .map((snapshot) => snapshot.docs
            .map((doc) => ChatMessage.fromJson(doc.data(), docId: doc.id))
            .toList());
  }

  Stream<List<Conversation>> conversationsStream(String userId) {
    return _firestore
        .collection('conversations')
        .where('participants', arrayContains: userId)
        .orderBy('lastMessageAt', descending: true)
        .snapshots()
        .map((snapshot) => snapshot.docs
            .map((doc) => Conversation.fromJson(doc.data(), docId: doc.id))
            .toList());
  }

  Future<void> resetUnreadCount(String conversationId, String userId) =>
      _firestore.collection('conversations').doc(conversationId).update({
        'unreadCount.$userId': 0,
      });

  Future<void> markMessageRead(String messageId) =>
      _firestore.collection('messages').doc(messageId).update({
        'readAt': FieldValue.serverTimestamp(),
      });

  Future<void> sendMessage({
    required String conversationId,
    required String senderId,
    required String receiverId,
    required String content,
  }) async {
    final now = FieldValue.serverTimestamp();

    await _firestore.collection('messages').add({
      'conversationId': conversationId,
      'senderId': senderId,
      'receiverId': receiverId,
      'content': content,
      'type': 'text',
      'createdAt': now,
      'readAt': null,
    });

    await _firestore.collection('conversations').doc(conversationId).update({
      'lastMessage': content,
      'lastMessageAt': now,
      'unreadCount.$receiverId': FieldValue.increment(1),
    });
  }
}
