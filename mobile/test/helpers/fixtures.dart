/// Canonical JSON fixtures used across tests.
///
/// Keep these in sync with what the backend actually returns — they
/// double as documentation of the wire format.
abstract class Fixtures {
  static Map<String, dynamic> get user => {
        'id': 'u_1',
        'displayName': 'Test User',
        'phone': '998901234567',
        'role': 'STAFF',
        'departmentId': 'd_1',
        'managerId': 'm_1',
        'isActive': true,
      };

  static Map<String, dynamic> get manager => {
        'id': 'm_1',
        'displayName': 'Test Manager',
        'phone': '998901111111',
        'role': 'MANAGER',
        'departmentId': 'd_1',
        'isActive': true,
      };

  static Map<String, dynamic> get task => {
        'id': 't_1',
        'title': 'Test task',
        'description': 'Just a test',
        'status': 'PENDING',
        'priority': 'MEDIUM',
        'assignedTo': 'u_1',
        'departmentId': 'd_1',
        'deadline': '2026-06-01T12:00:00Z',
        'createdAt': '2026-05-20T10:00:00Z',
      };

  static Map<String, dynamic> get kpi => {
        'id': 'k_1',
        'staffId': 'u_1',
        'period': '2026-05',
        'score': 87,
        'timeliness': 35,
        'completion': 27,
        'efficiency': 25,
        'totalTasks': 10,
        'completedTasks': 8,
      };
}
