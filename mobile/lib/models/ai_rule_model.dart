class AiRule {
  final String id;
  final String? departmentId;
  final String? managerId;
  final String title;
  final String content;
  final String category;
  final bool isActive;
  final int priority;

  AiRule({
    required this.id,
    this.departmentId,
    this.managerId,
    required this.title,
    required this.content,
    required this.category,
    required this.isActive,
    required this.priority,
  });

  factory AiRule.fromJson(Map<String, dynamic> json) {
    return AiRule(
      id: json['id']?.toString() ?? '',
      departmentId: json['departmentId']?.toString(),
      managerId: json['managerId']?.toString(),
      title: json['title'] ?? '',
      content: json['content'] ?? '',
      category: json['category'] ?? '',
      isActive: json['isActive'] ?? true,
      priority: json['priority'] ?? 0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'departmentId': departmentId,
      'managerId': managerId,
      'title': title,
      'content': content,
      'category': category,
      'isActive': isActive,
      'priority': priority,
    };
  }
}
