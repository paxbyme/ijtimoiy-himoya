import 'package:cloud_firestore/cloud_firestore.dart';

class Conversation {
  final String id;
  final List<String> participants;
  final String? lastMessage;
  final DateTime? lastMessageAt;
  final Map<String, int> unreadCount;

  Conversation({
    required this.id,
    required this.participants,
    this.lastMessage,
    this.lastMessageAt,
    required this.unreadCount,
  });

  factory Conversation.fromJson(Map<String, dynamic> json, {String? docId}) {
    final rawUnread = json['unreadCount'];
    final Map<String, int> parsedUnread = {};
    if (rawUnread is Map) {
      for (final entry in rawUnread.entries) {
        parsedUnread[entry.key.toString()] = (entry.value as num?)?.toInt() ?? 0;
      }
    }

    DateTime? lastMsgAt;
    if (json['lastMessageAt'] != null) {
      final val = json['lastMessageAt'];
      if (val is Timestamp) {
        lastMsgAt = val.toDate();
      } else if (val is String) {
        lastMsgAt = DateTime.tryParse(val);
      }
    }

    return Conversation(
      id: docId ?? json['id'] ?? '',
      participants: List<String>.from(json['participants'] ?? []),
      lastMessage: json['lastMessage'],
      lastMessageAt: lastMsgAt,
      unreadCount: parsedUnread,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'participants': participants,
      'lastMessage': lastMessage,
      'lastMessageAt': lastMessageAt != null
          ? Timestamp.fromDate(lastMessageAt!)
          : null,
      'unreadCount': unreadCount,
    };
  }
}
