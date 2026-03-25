import 'package:cloud_firestore/cloud_firestore.dart';
import '../models/chat_message_model.dart';
import '../models/conversation_model.dart';

class FirestoreService {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

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

  Future<void> markMessageRead(String messageId) async {
    await _firestore.collection('messages').doc(messageId).update({
      'readAt': FieldValue.serverTimestamp(),
    });
  }

  Future<void> sendMessage(
    String conversationId,
    String senderId,
    String receiverId,
    String content,
  ) async {
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
