class Department {
  final String id;
  final String name;
  final String? managerId;
  final String? createdAt;
  final String? updatedAt;

  const Department({
    required this.id,
    required this.name,
    this.managerId,
    this.createdAt,
    this.updatedAt,
  });

  factory Department.fromJson(Map<String, dynamic> json) {
    return Department(
      id: json['id']?.toString() ?? '',
      name: json['name'] ?? '',
      managerId: json['managerId']?.toString(),
      createdAt: json['createdAt']?.toString(),
      updatedAt: json['updatedAt']?.toString(),
    );
  }
}
