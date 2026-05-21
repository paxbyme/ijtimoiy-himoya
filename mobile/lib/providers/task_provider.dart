import 'package:dartz/dartz.dart' hide Task;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/error/failures.dart';
import '../data/datasources/remote/task_remote_datasource.dart';
import '../data/repositories/task_repository.dart';
import '../models/task/task_model.dart';
import 'auth_provider.dart';

final taskRemoteDataSourceProvider = Provider<TaskRemoteDataSource>(
    (ref) => TaskRemoteDataSource(ref.read(dioProvider)));

final taskRepositoryProvider = Provider<TaskRepository>((ref) {
  return TaskRepository(
    ref.read(taskRemoteDataSourceProvider),
    ref.read(networkInfoProvider),
  );
});

final myTasksProvider = FutureProvider<List<Task>>((ref) async {
  final result = await ref.read(taskRepositoryProvider).getMyTasks();
  return result.fold((f) => throw f, (tasks) => tasks);
});

final allTasksProvider = FutureProvider<List<Task>>((ref) async {
  final result = await ref.read(taskRepositoryProvider).getAllTasks();
  return result.fold((f) => throw f, (tasks) => tasks);
});

class TaskNotifier extends Notifier<AsyncValue<void>> {
  @override
  AsyncValue<void> build() => const AsyncValue.data(null);

  TaskRepository get _repo => ref.read(taskRepositoryProvider);

  void _invalidateLists() {
    ref.invalidate(allTasksProvider);
    ref.invalidate(myTasksProvider);
  }

  Future<bool> _run(Future<Either<Failure, dynamic>> Function() op) async {
    state = const AsyncValue.loading();
    final result = await op();
    return result.fold(
      (failure) {
        state = AsyncValue.error(failure, StackTrace.current);
        return false;
      },
      (_) {
        _invalidateLists();
        state = const AsyncValue.data(null);
        return true;
      },
    );
  }

  Future<bool> createTask(Map<String, dynamic> data) =>
      _run(() => _repo.createTask(data));

  Future<bool> createBulkTasks(
          List<String> assignedToList, Map<String, dynamic> data) =>
      _run(() => _repo.createBulkTasks(assignedToList, data));

  Future<bool> completeTask(String taskId) =>
      _run(() => _repo.completeTask(taskId));

  Future<bool> updateStatus(String taskId, String status) =>
      _run(() => _repo.updateStatus(taskId, status));

  Future<bool> acceptTask(String taskId) async {
    final result = await _repo.acceptTask(taskId);
    return result.fold((_) => false, (_) {
      _invalidateLists();
      return true;
    });
  }

  /// Returns `null` on success or a user-facing error message on failure.
  Future<String?> uploadAttachment(
      String taskId, String filePath, String fileName) async {
    final result = await _repo.uploadAttachment(taskId, filePath, fileName);
    return result.fold(
      (failure) => failure.message,
      (_) {
        _invalidateLists();
        return null;
      },
    );
  }
}

final taskNotifierProvider =
    NotifierProvider<TaskNotifier, AsyncValue<void>>(TaskNotifier.new);
