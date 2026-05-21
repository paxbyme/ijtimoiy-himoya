import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/constants/route_names.dart';
import '../../models/task/task_model.dart';
import '../../providers/task_provider.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';
import '../../widgets/task_card.dart';
import '../../widgets/app_background.dart';

class MyTasksScreen extends ConsumerStatefulWidget {
  const MyTasksScreen({super.key});

  @override
  ConsumerState<MyTasksScreen> createState() => _MyTasksScreenState();
}

class _MyTasksScreenState extends ConsumerState<MyTasksScreen>
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
    final tasksAsync = ref.watch(myTasksProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Mening topshiriqlarim'),
        bottom: TabBar(
          controller: _tabController,
          tabs: _tabs.map((t) => Tab(text: t)).toList(),
          isScrollable: true,
          tabAlignment: TabAlignment.start,
        ),
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
                onPressed: () => ref.invalidate(myTasksProvider),
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
                ref.invalidate(myTasksProvider);
                await ref.read(myTasksProvider.future);
              },
              child: ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: filtered.length,
                itemBuilder: (context, i) {
                  final task = filtered[i];
                  return TaskCard(
                    task: task,
                    onTap: () => context.push(Routes.staffTaskDetail(task.id)),
                  );
                },
              ),
            );
          }),
        ),
      )),
    );
  }
}
