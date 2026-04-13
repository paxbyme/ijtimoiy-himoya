class TaskAttachment {
  final String url;
  final String name;

  const TaskAttachment({required this.url, required this.name});

  factory TaskAttachment.fromJson(Map<String, dynamic> json) {
    return TaskAttachment(
      url: json['url']?.toString() ?? '',
      name: json['name']?.toString() ?? 'Fayl',
    );
  }

  Map<String, dynamic> toJson() => {'url': url, 'name': name};
}

class Task {
  final String id;
  final String title;
  final String description;
  final String assignedTo;
  final String assignedBy;
  final String? departmentId;
  final String status;
  final String priority;
  final DateTime? deadline;
  final DateTime? completedAt;
  final DateTime? createdAt;
  final String? assigneeName;
  final List<TaskAttachment> attachments;
  final bool managerAccepted;

  Task({
    required this.id,
    required this.title,
    required this.description,
    required this.assignedTo,
    required this.assignedBy,
    this.departmentId,
    required this.status,
    required this.priority,
    this.deadline,
    this.completedAt,
    this.createdAt,
    this.assigneeName,
    this.attachments = const [],
    this.managerAccepted = false,
  });

  // Backward compat getters for screens that still reference single attachment
  String? get attachmentUrl => attachments.isEmpty ? null : attachments.first.url;
  String? get attachmentName => attachments.isEmpty ? null : attachments.first.name;

  bool get isOverdue {
    if (deadline == null) return false;
    if (status == 'COMPLETED' || status == 'CANCELLED') return false;
    return deadline!.isBefore(DateTime.now());
  }

  factory Task.fromJson(Map<String, dynamic> json) {
    List<TaskAttachment> attachments = [];

    // Parse new attachments list field
    final attList = json['attachments'];
    if (attList is List && attList.isNotEmpty) {
      for (final item in attList) {
        if (item is Map) {
          final att = TaskAttachment.fromJson(Map<String, dynamic>.from(item));
          if (att.url.isNotEmpty) attachments.add(att);
        }
      }
    }

    // Backward compat: single attachment fields
    if (attachments.isEmpty && json['attachmentUrl'] != null) {
      attachments = [
        TaskAttachment(
          url: json['attachmentUrl'].toString(),
          name: json['attachmentName']?.toString() ?? 'Fayl',
        ),
      ];
    }

    return Task(
      id: json['id']?.toString() ?? '',
      title: json['title'] ?? '',
      description: json['description'] ?? '',
      assignedTo: json['assignedTo']?.toString() ?? '',
      assignedBy: json['assignedBy']?.toString() ?? '',
      departmentId: json['departmentId']?.toString(),
      status: json['status'] ?? 'NEW',
      priority: json['priority'] ?? 'MEDIUM',
      deadline: json['deadline'] != null ? DateTime.tryParse(json['deadline']) : null,
      completedAt: json['completedAt'] != null ? DateTime.tryParse(json['completedAt']) : null,
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt']) : null,
      assigneeName: json['assigneeName'],
      attachments: attachments,
      managerAccepted: json['managerAccepted'] == true,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'description': description,
      'assignedTo': assignedTo,
      'assignedBy': assignedBy,
      'departmentId': departmentId,
      'status': status,
      'priority': priority,
      'deadline': deadline?.toIso8601String(),
      'completedAt': completedAt?.toIso8601String(),
      'createdAt': createdAt?.toIso8601String(),
      'assigneeName': assigneeName,
      'attachments': attachments.map((a) => a.toJson()).toList(),
      'managerAccepted': managerAccepted,
    };
  }
}
