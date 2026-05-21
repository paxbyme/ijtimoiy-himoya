class AiConversation {
  final String id;
  final String staffId;
  final String departmentId;
  final String title;
  final int messageCount;
  final String? createdAt;
  final String? updatedAt;

  AiConversation({
    required this.id,
    required this.staffId,
    required this.departmentId,
    required this.title,
    required this.messageCount,
    this.createdAt,
    this.updatedAt,
  });

  factory AiConversation.fromJson(Map<String, dynamic> json) {
    return AiConversation(
      id: json['id'] ?? '',
      staffId: json['staffId'] ?? '',
      departmentId: json['departmentId'] ?? '',
      title: json['title'] ?? 'Untitled',
      messageCount: json['messageCount'] ?? 0,
      createdAt: json['createdAt']?.toString(),
      updatedAt: json['updatedAt']?.toString(),
    );
  }
}
