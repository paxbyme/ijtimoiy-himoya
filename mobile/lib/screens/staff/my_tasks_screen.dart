import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../providers/task_provider.dart';
import '../../widgets/task_card.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';

class MyTasksScreen extends ConsumerStatefulWidget {
  const MyTasksScreen({super.key});

  @override
  ConsumerState<MyTasksScreen> createState() => _MyTasksScreenState();
}

class _MyTasksScreenState extends ConsumerState<MyTasksScreen>
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
    final tasksAsync = ref.watch(myTasksProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('My Tasks'),
        bottom: TabBar(
          controller: _tabController,
          tabs: _tabs.map((t) => Tab(text: t)).toList(),
          isScrollable: true,
          tabAlignment: TabAlignment.start,
        ),
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
                onPressed: () => ref.invalidate(myTasksProvider),
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
                ref.invalidate(myTasksProvider);
                await ref.read(myTasksProvider.future);
              },
              child: ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: filtered.length,
                itemBuilder: (context, index) {
                  final task = filtered[index];
                  return TaskCard(
                    task: task,
                    onTap: () => context.push('/staff/tasks/${task.id}'),
                  );
                },
              ),
            );
          }).toList(),
        ),
      ),
    );
  }
}
