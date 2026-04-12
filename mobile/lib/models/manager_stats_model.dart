class ManagerStats {
  final String id;
  final String name;
  final String phone;
  final String? departmentId;
  final String? departmentName;
  final bool isActive;
  final String? createdAt;

  final int staffTotal;
  final int staffActive;

  final int taskTotal;
  final int taskCompleted;
  final int taskPending;
  final int taskInProgress;
  final int taskCancelled;

  final double? avgKpiScore;
  final String? currentPeriod;

  const ManagerStats({
    required this.id,
    required this.name,
    required this.phone,
    this.departmentId,
    this.departmentName,
    required this.isActive,
    this.createdAt,
    this.staffTotal = 0,
    this.staffActive = 0,
    this.taskTotal = 0,
    this.taskCompleted = 0,
    this.taskPending = 0,
    this.taskInProgress = 0,
    this.taskCancelled = 0,
    this.avgKpiScore,
    this.currentPeriod,
  });

  factory ManagerStats.fromJson(Map<String, dynamic> json) {
    return ManagerStats(
      id: json['id']?.toString() ?? '',
      name: json['name'] ?? '',
      phone: json['phone'] ?? '',
      departmentId: json['departmentId']?.toString(),
      departmentName: json['departmentName']?.toString(),
      isActive: json['isActive'] as bool? ?? true,
      createdAt: json['createdAt']?.toString(),
      staffTotal: (json['staffTotal'] as num?)?.toInt() ?? 0,
      staffActive: (json['staffActive'] as num?)?.toInt() ?? 0,
      taskTotal: (json['taskTotal'] as num?)?.toInt() ?? 0,
      taskCompleted: (json['taskCompleted'] as num?)?.toInt() ?? 0,
      taskPending: (json['taskPending'] as num?)?.toInt() ?? 0,
      taskInProgress: (json['taskInProgress'] as num?)?.toInt() ?? 0,
      taskCancelled: (json['taskCancelled'] as num?)?.toInt() ?? 0,
      avgKpiScore: (json['avgKpiScore'] as num?)?.toDouble(),
      currentPeriod: json['currentPeriod']?.toString(),
    );
  }
}
