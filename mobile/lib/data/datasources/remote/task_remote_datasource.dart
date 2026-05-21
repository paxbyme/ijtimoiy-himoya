import 'package:dio/dio.dart';

import '../../../models/task/task_model.dart';

/// Thin Dio wrapper for task REST calls. Error mapping happens in
/// [TaskRepository] — keep this dumb.
class TaskRemoteDataSource {
  final Dio _dio;

  TaskRemoteDataSource(this._dio);

  Future<List<Task>> getTasks() async {
    final response = await _dio.get('/tasks');
    final data = response.data['data'] ?? response.data;
    final List<dynamic> list = data is Map ? (data['content'] ?? []) : data;
    return list.map((e) => Task.fromJson(e)).toList();
  }

  /// Backend currently returns the caller's tasks from the same endpoint.
  Future<List<Task>> getMyTasks() => getTasks();

  Future<Task> createTask(Map<String, dynamic> data) async {
    final response = await _dio.post('/tasks', data: data);
    return Task.fromJson(response.data['data'] ?? response.data);
  }

  Future<List<Task>> createBulkTasks(
      List<String> assignedToList, Map<String, dynamic> taskData) async {
    final response = await _dio.post('/tasks/bulk', data: {
      ...taskData,
      'assignedToList': assignedToList,
    });
    final List<dynamic> list = response.data['data'] ?? response.data;
    return list.map((e) => Task.fromJson(e)).toList();
  }

  Future<Task> completeTask(String taskId) async {
    final response = await _dio.put('/tasks/$taskId/complete');
    return Task.fromJson(response.data['data'] ?? response.data);
  }

  Future<Task> updateTaskStatus(String taskId, String status) async {
    final response =
        await _dio.put('/tasks/$taskId', data: {'status': status});
    return Task.fromJson(response.data['data'] ?? response.data);
  }

  Future<Task> acceptTask(String taskId) async {
    final response = await _dio.put('/tasks/$taskId/accept');
    return Task.fromJson(response.data['data'] ?? response.data);
  }

  Future<Task> uploadAttachment(
      String taskId, String filePath, String fileName) async {
    final formData = FormData.fromMap({
      'file': await MultipartFile.fromFile(filePath, filename: fileName),
    });
    final response = await _dio.post(
      '/tasks/$taskId/attachment',
      data: formData,
    );
    return Task.fromJson(response.data['data'] ?? response.data);
  }
}
