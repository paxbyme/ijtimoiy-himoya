import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../models/task_model.dart';
import '../../providers/task_provider.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';
import '../../widgets/app_background.dart';

class TaskManagementScreen extends ConsumerStatefulWidget {
  const TaskManagementScreen({super.key});

  @override
  ConsumerState<TaskManagementScreen> createState() =>
      _TaskManagementScreenState();
}

class _TaskManagementScreenState extends ConsumerState<TaskManagementScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  final _tabs = const [
    'Hammasi',
    'Jarayonda',
    "Muddati o'tgan",
    'Bajarildi',
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _tabs.length, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  List<Task> _filterTasks(List<Task> tasks, int tabIndex) {
    switch (tabIndex) {
      case 0:
        return tasks;
      case 1:
        return tasks.where((t) => t.status == 'IN_PROGRESS').toList();
      case 2:
        return tasks.where((t) => t.isOverdue).toList();
      case 3:
        return tasks.where((t) => t.status == 'COMPLETED').toList();
      default:
        return tasks;
    }
  }

  @override
  Widget build(BuildContext context) {
    final tasksAsync = ref.watch(allTasksProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Topshiriqlar'),
        bottom: TabBar(
          controller: _tabController,
          tabs: _tabs.map((t) => Tab(text: t)).toList(),
          isScrollable: true,
          tabAlignment: TabAlignment.start,
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => context.push('/manager/tasks/create'),
        child: const Icon(Icons.add),
      ),
      body: AppBackground(child: tasksAsync.when(
        loading: () => const LoadingWidget(),
        error: (error, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Topshiriqlar yuklanmadi',
                  style: TextStyle(color: theme.colorScheme.error)),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => ref.invalidate(allTasksProvider),
                child: const Text('Qayta urinish'),
              ),
            ],
          ),
        ),
        data: (tasks) => TabBarView(
          controller: _tabController,
          children: List.generate(_tabs.length, (index) {
            final filtered = _filterTasks(tasks, index);

            if (filtered.isEmpty) {
              return EmptyStateWidget(
                icon: Icons.task_alt,
                message: index == 2
                    ? "Muddati o'tgan topshiriqlar yo'q"
                    : 'Topshiriqlar topilmadi',
              );
            }

            return RefreshIndicator(
              onRefresh: () async {
                ref.invalidate(allTasksProvider);
                await ref.read(allTasksProvider.future);
              },
              child: ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: filtered.length,
                itemBuilder: (context, i) {
                  final task = filtered[i];
                  return _TaskManagerCard(
                    task: task,
                    onAccept: task.status == 'COMPLETED' &&
                            task.attachments.isNotEmpty &&
                            !task.managerAccepted
                        ? () => _acceptTask(task)
                        : null,
                  );
                },
              ),
            );
          }),
        ),
      )),
    );
  }

  void _acceptTask(Task task) async {
    final notifier = ref.read(taskNotifierProvider.notifier);
    final success = await notifier.acceptTask(task.id);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(success ? 'Qabul qilindi' : 'Xatolik yuz berdi'),
        ),
      );
    }
  }
}

bool _isImage(String? name) {
  if (name == null) return false;
  final ext = name.split('.').last.toLowerCase();
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].contains(ext);
}

Future<void> _openAttachment(BuildContext context, String url, String? name) async {
  if (_isImage(name)) {
    await showDialog(
      context: context,
      builder: (ctx) => Dialog(
        backgroundColor: Colors.black,
        insetPadding: EdgeInsets.zero,
        child: Stack(
          children: [
            InteractiveViewer(
              child: Center(
                child: Image.network(
                  url,
                  fit: BoxFit.contain,
                  loadingBuilder: (c, child, progress) => progress == null
                      ? child
                      : const Center(child: CircularProgressIndicator()),
                  errorBuilder: (c, e, s) => const Center(
                    child: Text('Rasm yuklanmadi',
                        style: TextStyle(color: Colors.white)),
                  ),
                ),
              ),
            ),
            Positioned(
              top: 8,
              right: 8,
              child: IconButton(
                icon: const Icon(Icons.close, color: Colors.white, size: 28),
                onPressed: () => Navigator.pop(ctx),
              ),
            ),
          ],
        ),
      ),
    );
  } else {
    final uri = Uri.parse(url);
    bool opened = false;
    try {
      opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {}
    if (!opened) {
      try {
        await launchUrl(uri, mode: LaunchMode.platformDefault);
      } catch (_) {}
    }
  }
}

class _TaskManagerCard extends StatelessWidget {
  final Task task;
  final VoidCallback? onAccept;

  const _TaskManagerCard({required this.task, this.onAccept});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isOverdue = task.isOverdue;

    Color statusColor;
    String statusLabel;
    if (isOverdue) {
      statusColor = Colors.red;
      statusLabel = "Muddati o'tgan";
    } else {
      switch (task.status) {
        case 'NEW':
        case 'PENDING':
          statusColor = Colors.orange;
          statusLabel = 'Yangi';
          break;
        case 'IN_PROGRESS':
          statusColor = Colors.blue;
          statusLabel = 'Jarayonda';
          break;
        case 'COMPLETED':
          statusColor = Colors.green;
          statusLabel = 'Bajarildi';
          break;
        case 'CANCELLED':
          statusColor = Colors.grey;
          statusLabel = 'Bekor qilingan';
          break;
        default:
          statusColor = Colors.grey;
          statusLabel = task.status;
      }
    }

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    task.title,
                    style: theme.textTheme.titleSmall
                        ?.copyWith(fontWeight: FontWeight.w600),
                  ),
                ),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: statusColor.withValues(alpha: 0.3)),
                  ),
                  child: Text(
                    statusLabel,
                    style: TextStyle(
                        color: statusColor,
                        fontSize: 11,
                        fontWeight: FontWeight.w500),
                  ),
                ),
              ],
            ),
            if (task.assigneeName != null) ...[
              const SizedBox(height: 4),
              Text(
                task.assigneeName!,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
            if (task.attachments.isNotEmpty) ...[
              const SizedBox(height: 8),
              ...task.attachments.map((att) => Padding(
                padding: const EdgeInsets.only(top: 4),
                child: GestureDetector(
                  onTap: () => _openAttachment(context, att.url, att.name),
                  child: Row(
                    children: [
                      Icon(
                        _isImage(att.name) ? Icons.image_outlined : Icons.attach_file,
                        size: 14,
                        color: theme.colorScheme.primary,
                      ),
                      const SizedBox(width: 4),
                      Expanded(
                        child: Text(
                          att.name,
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.primary,
                            decoration: TextDecoration.underline,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Icon(Icons.open_in_new,
                          size: 12, color: theme.colorScheme.primary),
                    ],
                  ),
                ),
              )),
            ],
            if (onAccept != null) ...[
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: onAccept,
                  icon: const Icon(Icons.check_circle, size: 16),
                  label: const Text('Qabul qildim'),
                  style: FilledButton.styleFrom(
                    backgroundColor: Colors.green,
                    padding: const EdgeInsets.symmetric(vertical: 8),
                  ),
                ),
              ),
            ],
            if (task.managerAccepted) ...[
              const SizedBox(height: 4),
              Row(
                children: [
                  const Icon(Icons.verified, size: 14, color: Colors.green),
                  const SizedBox(width: 4),
                  Text(
                    'Qabul qilingan',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: Colors.green),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}
