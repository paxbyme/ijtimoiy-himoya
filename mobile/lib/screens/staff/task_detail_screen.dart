import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../models/task_model.dart';
import '../../providers/task_provider.dart';
import '../../widgets/loading_widget.dart';

class TaskDetailScreen extends ConsumerWidget {
  final String taskId;

  const TaskDetailScreen({super.key, required this.taskId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tasksAsync = ref.watch(myTasksProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Task Details'),
      ),
      body: tasksAsync.when(
        loading: () => const LoadingWidget(),
        error: (error, _) => Center(child: Text('Error: $error')),
        data: (tasks) {
          final task = tasks.cast<Task?>().firstWhere(
                (t) => t!.id == taskId,
                orElse: () => null,
              );

          if (task == null) {
            return const Center(child: Text('Task not found'));
          }

          return SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Title
                Text(
                  task.title,
                  style: theme.textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 12),

                // Status & Priority
                Row(
                  children: [
                    _buildStatusChip(context, task.status),
                    const SizedBox(width: 8),
                    _buildPriorityChip(context, task.priority),
                  ],
                ),
                const SizedBox(height: 24),

                // Description
                Text(
                  'Description',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  task.description.isNotEmpty
                      ? task.description
                      : 'No description provided',
                  style: theme.textTheme.bodyLarge?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 24),

                // Details card
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      children: [
                        _buildDetailRow(
                          context,
                          'Assigned By',
                          task.assignedBy,
                          Icons.person_outline,
                        ),
                        if (task.assigneeName != null) ...[
                          const Divider(),
                          _buildDetailRow(
                            context,
                            'Assignee',
                            task.assigneeName!,
                            Icons.person,
                          ),
                        ],
                        if (task.deadline != null) ...[
                          const Divider(),
                          _buildDetailRow(
                            context,
                            'Deadline',
                            DateFormat('MMM d, yyyy').format(task.deadline!),
                            Icons.calendar_today,
                          ),
                        ],
                        if (task.createdAt != null) ...[
                          const Divider(),
                          _buildDetailRow(
                            context,
                            'Created',
                            DateFormat('MMM d, yyyy').format(task.createdAt!),
                            Icons.access_time,
                          ),
                        ],
                        if (task.completedAt != null) ...[
                          const Divider(),
                          _buildDetailRow(
                            context,
                            'Completed',
                            DateFormat('MMM d, yyyy').format(task.completedAt!),
                            Icons.check_circle_outline,
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 32),

                // Complete button
                if (task.status != 'COMPLETED')
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: () => _confirmComplete(context, ref, task),
                      icon: const Icon(Icons.check_circle),
                      label: const Text('Mark as Complete'),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }

  void _confirmComplete(BuildContext context, WidgetRef ref, Task task) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Complete Task'),
        content: Text('Mark "${task.title}" as completed?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              Navigator.pop(ctx);
              final success =
                  await ref.read(taskNotifierProvider.notifier).completeTask(task.id);
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(
                      success ? 'Task completed!' : 'Failed to complete task',
                    ),
                  ),
                );
              }
            },
            child: const Text('Complete'),
          ),
        ],
      ),
    );
  }

  Widget _buildStatusChip(BuildContext context, String status) {
    final theme = Theme.of(context);
    Color color;
    switch (status) {
      case 'COMPLETED':
        color = Colors.green;
        break;
      case 'IN_PROGRESS':
        color = Colors.orange;
        break;
      default:
        color = theme.colorScheme.primary;
    }

    return Chip(
      label: Text(
        status.replaceAll('_', ' '),
        style: TextStyle(color: color, fontSize: 12),
      ),
      side: BorderSide(color: color),
      backgroundColor: color.withValues(alpha: 0.1),
      padding: EdgeInsets.zero,
      visualDensity: VisualDensity.compact,
    );
  }

  Widget _buildPriorityChip(BuildContext context, String priority) {
    Color color;
    switch (priority) {
      case 'HIGH':
        color = Colors.red;
        break;
      case 'MEDIUM':
        color = Colors.orange;
        break;
      default:
        color = Colors.blue;
    }

    return Chip(
      label: Text(
        priority,
        style: TextStyle(color: color, fontSize: 12),
      ),
      side: BorderSide(color: color),
      backgroundColor: color.withValues(alpha: 0.1),
      padding: EdgeInsets.zero,
      visualDensity: VisualDensity.compact,
    );
  }

  Widget _buildDetailRow(
      BuildContext context, String label, String value, IconData icon) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(icon, size: 20, color: theme.colorScheme.onSurfaceVariant),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              Text(value, style: theme.textTheme.bodyMedium),
            ],
          ),
        ],
      ),
    );
  }
}
