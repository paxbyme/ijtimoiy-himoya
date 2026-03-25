import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/task_model.dart';
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
}

final taskNotifierProvider =
    NotifierProvider<TaskNotifier, AsyncValue<void>>(TaskNotifier.new);
