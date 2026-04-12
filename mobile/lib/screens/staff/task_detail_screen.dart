import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:file_picker/file_picker.dart';
import '../../models/task_model.dart';
import '../../providers/task_provider.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/loading_widget.dart';

class TaskDetailScreen extends ConsumerStatefulWidget {
  final String taskId;

  const TaskDetailScreen({super.key, required this.taskId});

  @override
  ConsumerState<TaskDetailScreen> createState() => _TaskDetailScreenState();
}

class _TaskDetailScreenState extends ConsumerState<TaskDetailScreen> {
  bool _isUploading = false;

  Future<void> _pickAndUploadFile(Task task) async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.any,
      allowMultiple: false,
    );
    if (result == null || result.files.isEmpty) return;
    final file = result.files.first;
    if (file.path == null) return;

    setState(() => _isUploading = true);
    try {
      final success = await ref
          .read(taskNotifierProvider.notifier)
          .uploadAttachment(task.id, file.path!, file.name);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
                success ? 'Fayl muvaffaqiyatli yuklandi' : 'Yuklashda xatolik'),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isUploading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final tasksAsync = ref.watch(myTasksProvider);
    final userProfile = ref.watch(userProfileProvider);
    final theme = Theme.of(context);

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) context.pop();
      },
      child: Scaffold(
        appBar: AppBar(
          leading: BackButton(onPressed: () => context.pop()),
          title: const Text('Topshiriq tafsiloti'),
        ),
        body: tasksAsync.when(
          loading: () => const LoadingWidget(),
          error: (error, _) => Center(child: Text('Xatolik: $error')),
          data: (tasks) {
            final task = tasks.cast<Task?>().firstWhere(
                  (t) => t!.id == widget.taskId,
                  orElse: () => null,
                );

            if (task == null) {
              return const Center(child: Text('Topshiriq topilmadi'));
            }

            return SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    task.title,
                    style: theme.textTheme.headlineSmall
                        ?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 12),

                  Row(
                    children: [
                      _buildStatusChip(context, task),
                      const SizedBox(width: 8),
                      _buildPriorityChip(context, task.priority),
                    ],
                  ),
                  const SizedBox(height: 24),

                  Text(
                    'Tavsif',
                    style: theme.textTheme.titleMedium
                        ?.copyWith(fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    task.description.isNotEmpty
                        ? task.description
                        : 'Tavsif yo\'q',
                    style: theme.textTheme.bodyLarge?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 24),

                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        children: [
                          _buildDetailRow(
                            context,
                            'Tayinlagan',
                            userProfile.maybeWhen(
                              data: (u) =>
                                  u?.managerId == task.assignedBy
                                      ? 'Menejer'
                                      : task.assignedBy,
                              orElse: () => task.assignedBy,
                            ),
                            Icons.person_outline,
                          ),
                          if (task.deadline != null) ...[
                            const Divider(),
                            _buildDetailRow(
                              context,
                              'Muddat',
                              DateFormat('dd.MM.yyyy').format(task.deadline!),
                              Icons.calendar_today,
                            ),
                          ],
                          if (task.completedAt != null) ...[
                            const Divider(),
                            _buildDetailRow(
                              context,
                              'Bajarilgan sana',
                              DateFormat('dd.MM.yyyy')
                                  .format(task.completedAt!),
                              Icons.check_circle_outline,
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Attachment section
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Icon(Icons.attach_file,
                                  size: 20,
                                  color: theme.colorScheme.primary),
                              const SizedBox(width: 8),
                              Text(
                                'Fayl',
                                style: theme.textTheme.titleSmall?.copyWith(
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          if (task.attachmentUrl != null) ...[
                            Row(
                              children: [
                                Icon(Icons.description,
                                    size: 16,
                                    color: theme.colorScheme.primary),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Text(
                                    task.attachmentName ?? 'Yuklangan fayl',
                                    style:
                                        theme.textTheme.bodyMedium?.copyWith(
                                      color: theme.colorScheme.primary,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            if (task.managerAccepted) ...[
                              const SizedBox(height: 8),
                              Row(
                                children: [
                                  const Icon(Icons.verified,
                                      size: 16, color: Colors.green),
                                  const SizedBox(width: 4),
                                  Text(
                                    'Menejer tomonidan qabul qilingan',
                                    style: theme.textTheme.bodySmall
                                        ?.copyWith(color: Colors.green),
                                  ),
                                ],
                              ),
                            ],
                          ] else
                            Text(
                              'Hali fayl yuklanmagan',
                              style: theme.textTheme.bodyMedium?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          const SizedBox(height: 12),
                          if (task.status != 'COMPLETED')
                            SizedBox(
                              width: double.infinity,
                              child: OutlinedButton.icon(
                                onPressed:
                                    _isUploading ? null : () => _pickAndUploadFile(task),
                                icon: _isUploading
                                    ? const SizedBox(
                                        width: 16,
                                        height: 16,
                                        child: CircularProgressIndicator(
                                            strokeWidth: 2),
                                      )
                                    : const Icon(Icons.upload_file, size: 18),
                                label: Text(_isUploading
                                    ? 'Yuklanmoqda...'
                                    : task.attachmentUrl != null
                                        ? 'Faylni yangilash'
                                        : 'Fayl yuklash'),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),

                  if (task.status != 'COMPLETED')
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        onPressed: () => _confirmComplete(context, ref, task),
                        icon: const Icon(Icons.check_circle),
                        label: const Text('Bajarildi deb belgilash'),
                      ),
                    ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }

  void _confirmComplete(BuildContext context, WidgetRef ref, Task task) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Topshiriqni yakunlash'),
        content: Text('"${task.title}" ni bajarildi deb belgilash?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Bekor'),
          ),
          FilledButton(
            onPressed: () async {
              Navigator.pop(ctx);
              final success = await ref
                  .read(taskNotifierProvider.notifier)
                  .completeTask(task.id);
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(success
                        ? 'Topshiriq bajarildi!'
                        : 'Yakunlashda xatolik'),
                  ),
                );
              }
            },
            child: const Text('Bajarildi'),
          ),
        ],
      ),
    );
  }

  Widget _buildStatusChip(BuildContext context, Task task) {
    Color color;
    String label;
    if (task.isOverdue) {
      color = Colors.red;
      label = "Muddati o'tgan";
    } else {
      switch (task.status) {
        case 'NEW':
          color = Colors.orange;
          label = 'Yangi';
          break;
        case 'IN_PROGRESS':
          color = Colors.blue;
          label = 'Jarayonda';
          break;
        case 'COMPLETED':
          color = Colors.green;
          label = 'Bajarildi';
          break;
        default:
          color = Colors.grey;
          label = task.status.replaceAll('_', ' ');
      }
    }

    return Chip(
      label: Text(label, style: TextStyle(color: color, fontSize: 12)),
      side: BorderSide(color: color),
      backgroundColor: color.withValues(alpha: 0.1),
      padding: EdgeInsets.zero,
      visualDensity: VisualDensity.compact,
    );
  }

  Widget _buildPriorityChip(BuildContext context, String priority) {
    Color color;
    String label;
    switch (priority) {
      case 'HIGH':
        color = Colors.red;
        label = 'Yuqori';
        break;
      case 'MEDIUM':
        color = Colors.orange;
        label = "O'rta";
        break;
      case 'URGENT':
        color = Colors.deepOrange;
        label = 'Shoshilinch';
        break;
      default:
        color = Colors.blue;
        label = 'Past';
    }

    return Chip(
      label: Text(label, style: TextStyle(color: color, fontSize: 12)),
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
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
              Text(value, style: theme.textTheme.bodyMedium),
            ],
          ),
        ],
      ),
    );
  }
}
