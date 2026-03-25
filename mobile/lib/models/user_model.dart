class User {
  final String id;
  final String phone;
  final String displayName;
  final String role;
  final String? departmentId;
  final String? managerId;
  final bool isActive;

  User({
    required this.id,
    required this.phone,
    required this.displayName,
    required this.role,
    this.departmentId,
    this.managerId,
    required this.isActive,
  });

  bool get isManager => role == 'MANAGER';

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id']?.toString() ?? '',
      phone: json['phone'] ?? '',
      displayName: json['displayName'] ?? '',
      role: json['role'] ?? '',
      departmentId: json['departmentId']?.toString(),
      managerId: json['managerId']?.toString(),
      isActive: json['isActive'] ?? true,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'phone': phone,
      'displayName': displayName,
      'role': role,
      'departmentId': departmentId,
      'managerId': managerId,
      'isActive': isActive,
    };
  }
}
