import 'package:dio/dio.dart';

import '../../../models/auth/user_model.dart';
import '../../../models/kpi/manager_stats_model.dart';
import '../../../models/task/department_model.dart';

class AdminRemoteDataSource {
  final Dio _dio;
  AdminRemoteDataSource(this._dio);

  // ---- Staff (department-scoped, used by manager UIs) ----

  Future<List<User>> getStaffList() async {
    final response = await _dio.get('/users/staff');
    final data = response.data['data'] ?? response.data;
    final List<dynamic> list = data is Map ? (data['content'] ?? []) : data;
    return list.map((e) => User.fromJson(e)).toList();
  }

  Future<User> createStaff(Map<String, dynamic> data) async {
    final response = await _dio.post('/users/staff', data: data);
    return User.fromJson(response.data['data'] ?? response.data);
  }

  // ---- Managers (DEVELOPER role) ----

  Future<List<User>> getManagers() async {
    final response = await _dio.get('/admin/managers?page=0&size=100');
    final data = response.data['data'] ?? response.data;
    final List<dynamic> list = data is Map ? (data['content'] ?? []) : data;
    return list.map((e) => User.fromJson(e)).toList();
  }

  Future<User> createManager(Map<String, dynamic> data) async {
    final response = await _dio.post('/admin/managers', data: data);
    return User.fromJson(response.data['data'] ?? response.data);
  }

  Future<User> updateManager(String id, Map<String, dynamic> data) async {
    final response = await _dio.put('/admin/managers/$id', data: data);
    return User.fromJson(response.data['data'] ?? response.data);
  }

  Future<void> deactivateManager(String id) =>
      _dio.delete('/admin/managers/$id');

  Future<void> hardDeleteManager(String id) =>
      _dio.delete('/admin/managers/$id/hard');

  Future<ManagerStats> getManagerStats(String id) async {
    final response = await _dio.get('/admin/managers/$id/stats');
    return ManagerStats.fromJson(response.data['data'] ?? response.data);
  }

  // ---- Departments ----

  Future<List<Department>> getDepartments() async {
    final response = await _dio.get('/admin/departments?page=0&size=100');
    final data = response.data['data'] ?? response.data;
    final List<dynamic> list = data is Map ? (data['content'] ?? []) : data;
    return list.map((e) => Department.fromJson(e)).toList();
  }

  Future<Department> createDepartment(Map<String, dynamic> data) async {
    final response = await _dio.post('/admin/departments', data: data);
    return Department.fromJson(response.data['data'] ?? response.data);
  }

  Future<Department> updateDepartment(
      String id, Map<String, dynamic> data) async {
    final response = await _dio.put('/admin/departments/$id', data: data);
    return Department.fromJson(response.data['data'] ?? response.data);
  }

  Future<void> deleteDepartment(String id) =>
      _dio.delete('/admin/departments/$id');
}
