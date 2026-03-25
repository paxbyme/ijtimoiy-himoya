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
  });

  factory Task.fromJson(Map<String, dynamic> json) {
    return Task(
      id: json['id']?.toString() ?? '',
      title: json['title'] ?? '',
      description: json['description'] ?? '',
      assignedTo: json['assignedTo']?.toString() ?? '',
      assignedBy: json['assignedBy']?.toString() ?? '',
      departmentId: json['departmentId']?.toString(),
      status: json['status'] ?? 'PENDING',
      priority: json['priority'] ?? 'MEDIUM',
      deadline: json['deadline'] != null ? DateTime.tryParse(json['deadline']) : null,
      completedAt: json['completedAt'] != null ? DateTime.tryParse(json['completedAt']) : null,
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt']) : null,
      assigneeName: json['assigneeName'],
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
    };
  }
}
