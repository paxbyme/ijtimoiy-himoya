import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/task/task_model.dart';
import 'auth_provider.dart';

final myTasksProvider = FutureProvider<List<Task>>((ref) async {
  final api = ref.read(apiServiceProvider);
  return api.getMyTasks();
});

final allTasksProvider = FutureProvider<List<Task>>((ref) async {
  final api = ref.read(apiServiceProvider);
  return api.getTasks();
});

class TaskNotifier extends Notifier<AsyncValue<void>> {
  @override
  AsyncValue<void> build() => const AsyncValue.data(null);

  Future<bool> createTask(Map<String, dynamic> data) async {
    state = const AsyncValue.loading();
    try {
      await ref.read(apiServiceProvider).createTask(data);
      ref.invalidate(allTasksProvider);
      ref.invalidate(myTasksProvider);
      state = const AsyncValue.data(null);
      return true;
    } catch (e, st) {
      state = AsyncValue.error(e, st);
      return false;
    }
  }

  Future<bool> createBulkTasks(List<String> assignedToList, Map<String, dynamic> data) async {
    state = const AsyncValue.loading();
    try {
      await ref.read(apiServiceProvider).createBulkTasks(assignedToList, data);
      ref.invalidate(allTasksProvider);
      ref.invalidate(myTasksProvider);
      state = const AsyncValue.data(null);
      return true;
    } catch (e, st) {
      state = AsyncValue.error(e, st);
      return false;
    }
  }

  Future<bool> completeTask(String taskId) async {
    state = const AsyncValue.loading();
    try {
      await ref.read(apiServiceProvider).completeTask(taskId);
      ref.invalidate(allTasksProvider);
      ref.invalidate(myTasksProvider);
      state = const AsyncValue.data(null);
      return true;
    } catch (e, st) {
      state = AsyncValue.error(e, st);
      return false;
    }
  }

  Future<bool> updateStatus(String taskId, String status) async {
    state = const AsyncValue.loading();
    try {
      await ref.read(apiServiceProvider).updateTaskStatus(taskId, status);
      ref.invalidate(allTasksProvider);
      ref.invalidate(myTasksProvider);
      state = const AsyncValue.data(null);
      return true;
    } catch (e, st) {
      state = AsyncValue.error(e, st);
      return false;
    }
  }

  Future<String?> uploadAttachment(String taskId, String filePath, String fileName) async {
    try {
      await ref.read(apiServiceProvider).uploadTaskAttachment(taskId, filePath, fileName);
      ref.invalidate(allTasksProvider);
      ref.invalidate(myTasksProvider);
      return null; // null = success
    } catch (e) {
      // ignore: avoid_print
      print('[uploadAttachment] error: $e');
      if (e is DioException) {
        final data = e.response?.data;
        if (data is Map && data['message'] != null) {
          return data['message'].toString();
        }
        return 'Server xatosi: ${e.response?.statusCode ?? e.type.name}';
      }
      return e.toString();
    }
  }

  Future<bool> acceptTask(String taskId) async {
    try {
      await ref.read(apiServiceProvider).acceptTask(taskId);
      ref.invalidate(allTasksProvider);
      ref.invalidate(myTasksProvider);
      return true;
    } catch (e) {
      return false;
    }
  }
}

final taskNotifierProvider =
    NotifierProvider<TaskNotifier, AsyncValue<void>>(TaskNotifier.new);
