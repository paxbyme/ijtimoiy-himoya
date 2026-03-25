import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../providers/task_provider.dart';
import '../../widgets/task_card.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';

class TaskManagementScreen extends ConsumerStatefulWidget {
  const TaskManagementScreen({super.key});

  @override
  ConsumerState<TaskManagementScreen> createState() =>
      _TaskManagementScreenState();
}

class _TaskManagementScreenState extends ConsumerState<TaskManagementScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  final _tabs = const ['All', 'Pending', 'In Progress', 'Completed'];
  final _statusFilters = const ['', 'PENDING', 'IN_PROGRESS', 'COMPLETED'];

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

  @override
  Widget build(BuildContext context) {
    final tasksAsync = ref.watch(allTasksProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Task Management'),
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
      body: tasksAsync.when(
        loading: () => const LoadingWidget(),
        error: (error, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Failed to load tasks',
                  style: TextStyle(color: theme.colorScheme.error)),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => ref.invalidate(allTasksProvider),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
        data: (tasks) => TabBarView(
          controller: _tabController,
          children: _statusFilters.map((filter) {
            final filtered = filter.isEmpty
                ? tasks
                : tasks.where((t) => t.status == filter).toList();

            if (filtered.isEmpty) {
              return const EmptyStateWidget(
                icon: Icons.task_alt,
                message: 'No tasks found',
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
                itemBuilder: (context, index) {
                  final task = filtered[index];
                  return TaskCard(task: task);
                },
              ),
            );
          }).toList(),
        ),
      ),
    );
  }
}
