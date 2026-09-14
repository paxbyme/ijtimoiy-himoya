import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/models/task/task_model.dart';

Task _task({required String status, DateTime? createdAt}) => Task(
      id: 't1',
      title: 'Task',
      description: '',
      assignedTo: 'staff',
      assignedBy: 'manager',
      status: status,
      priority: 'MEDIUM',
      createdAt: createdAt,
    );

void main() {
  group('Task.isRecentlyAdded', () {
    final now = DateTime.now();

    test('true for a NEW task created within 3 days', () {
      final task = _task(
        status: 'NEW',
        createdAt: now.subtract(const Duration(days: 2, hours: 23)),
      );
      expect(task.isRecentlyAdded, isTrue);
    });

    test('false for a NEW task created more than 3 days ago', () {
      final task = _task(
        status: 'NEW',
        createdAt: now.subtract(const Duration(days: 3, minutes: 1)),
      );
      expect(task.isRecentlyAdded, isFalse);
    });

    test('false for a recent task that is no longer NEW', () {
      final task = _task(status: 'IN_PROGRESS', createdAt: now);
      expect(task.isRecentlyAdded, isFalse);
    });

    test('false when createdAt is missing', () {
      expect(_task(status: 'NEW').isRecentlyAdded, isFalse);
    });
  });
}
