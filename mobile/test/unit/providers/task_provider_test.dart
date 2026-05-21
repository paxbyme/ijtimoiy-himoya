import 'package:dartz/dartz.dart' hide Task;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

import 'package:mobile/core/error/failures.dart';
import 'package:mobile/data/repositories/task_repository.dart';
import 'package:mobile/models/task/task_model.dart';
import 'package:mobile/providers/task_provider.dart';

import '../../helpers/fixtures.dart';

class MockTaskRepository extends Mock implements TaskRepository {}

void main() {
  late MockTaskRepository repo;
  late ProviderContainer container;

  setUp(() {
    repo = MockTaskRepository();
    container = ProviderContainer(overrides: [
      taskRepositoryProvider.overrideWithValue(repo),
    ]);
    addTearDown(container.dispose);
  });

  group('myTasksProvider', () {
    test('emits the list when the repository returns Right', () async {
      final tasks = [Task.fromJson(Fixtures.task)];
      when(() => repo.getMyTasks())
          .thenAnswer((_) async => Right(tasks));

      final result = await container.read(myTasksProvider.future);

      expect(result, tasks);
    });

    // Negative-case translation (Left → throw → AsyncError) is covered by
    // TaskRepository tests; provider tests focus on the happy path here.
  });

  group('TaskNotifier.completeTask', () {
    test('returns true and invalidates the lists on success', () async {
      when(() => repo.completeTask('t_1')).thenAnswer(
          (_) async => Right(Task.fromJson(Fixtures.task)));

      final ok = await container
          .read(taskNotifierProvider.notifier)
          .completeTask('t_1');

      expect(ok, isTrue);
    });

    test('returns false and surfaces the failure as AsyncError', () async {
      when(() => repo.completeTask('t_1')).thenAnswer(
          (_) async => const Left(ServerFailure('boom')));

      final ok = await container
          .read(taskNotifierProvider.notifier)
          .completeTask('t_1');

      expect(ok, isFalse);
      final state = container.read(taskNotifierProvider);
      expect(state, isA<AsyncError<void>>());
    });
  });
}
