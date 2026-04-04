import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../providers/auth_provider.dart';
import '../../providers/task_provider.dart';
import '../../providers/kpi_provider.dart';
import '../../widgets/stat_card.dart';

class ManagerHomeScreen extends ConsumerWidget {
  const ManagerHomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userProfile = ref.watch(userProfileProvider);
    final tasksAsync = ref.watch(allTasksProvider);
    final kpiAsync = ref.watch(kpiRankingsProvider);
    final staffAsync = ref.watch(_staffListProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Dashboard'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'Sign out',
            onPressed: () async {
              await ref.read(authServiceProvider).signOut();
              if (context.mounted) context.go('/login');
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(allTasksProvider);
          ref.invalidate(kpiRankingsProvider);
          ref.invalidate(_staffListProvider);
        },
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Greeting
              userProfile.when(
                data: (user) => Text(
                  'Hello, ${user?.displayName ?? "Manager"}',
                  style: theme.textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                loading: () => const SizedBox.shrink(),
                error: (_, __) => const SizedBox.shrink(),
              ),
              const SizedBox(height: 4),
              Text(
                'Here\'s an overview of your team',
                style: theme.textTheme.bodyLarge?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 24),

              // Stats grid
              GridView.count(
                crossAxisCount: 2,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
                childAspectRatio: 1.3,
                children: [
                  staffAsync.when(
                    data: (staff) => StatCard(
                      icon: Icons.people,
                      title: 'Employees',
                      value: '${staff.length}',
                      color: Colors.blue,
                    ),
                    loading: () => const StatCard(
                      icon: Icons.people,
                      title: 'Employees',
                      value: '...',
                      color: Colors.blue,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.people,
                      title: 'Employees',
                      value: '-',
                      color: Colors.blue,
                    ),
                  ),
                  tasksAsync.when(
                    data: (tasks) => StatCard(
                      icon: Icons.task_alt,
                      title: 'Total Tasks',
                      value: '${tasks.length}',
                      color: Colors.green,
                    ),
                    loading: () => const StatCard(
                      icon: Icons.task_alt,
                      title: 'Total Tasks',
                      value: '...',
                      color: Colors.green,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.task_alt,
                      title: 'Total Tasks',
                      value: '-',
                      color: Colors.green,
                    ),
                  ),
                  tasksAsync.when(
                    data: (tasks) {
                      final pending =
                          tasks.where((t) => t.status == 'PENDING').length;
                      return StatCard(
                        icon: Icons.pending_actions,
                        title: 'Pending',
                        value: '$pending',
                        color: Colors.orange,
                      );
                    },
                    loading: () => const StatCard(
                      icon: Icons.pending_actions,
                      title: 'Pending',
                      value: '...',
                      color: Colors.orange,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.pending_actions,
                      title: 'Pending',
                      value: '-',
                      color: Colors.orange,
                    ),
                  ),
                  kpiAsync.when(
                    data: (rankings) {
                      final avg = rankings.isEmpty
                          ? 0.0
                          : rankings.map((k) => k.score).reduce((a, b) => a + b) /
                              rankings.length;
                      return StatCard(
                        icon: Icons.bar_chart,
                        title: 'Avg KPI',
                        value: avg.toStringAsFixed(1),
                        color: Colors.purple,
                      );
                    },
                    loading: () => const StatCard(
                      icon: Icons.bar_chart,
                      title: 'Avg KPI',
                      value: '...',
                      color: Colors.purple,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.bar_chart,
                      title: 'Avg KPI',
                      value: '-',
                      color: Colors.purple,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Quick actions
              Text(
                'Quick Actions',
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 12),
              _buildQuickAction(
                context,
                icon: Icons.person_add,
                title: 'Add Employee',
                onTap: () => context.go('/manager/employees'),
              ),
              _buildQuickAction(
                context,
                icon: Icons.add_task,
                title: 'Create Task',
                onTap: () => context.push('/manager/tasks/create'),
              ),
              _buildQuickAction(
                context,
                icon: Icons.rule,
                title: 'Manage AI Rules',
                onTap: () => context.go('/manager/ai-rules'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildQuickAction(
    BuildContext context, {
    required IconData icon,
    required String title,
    required VoidCallback onTap,
  }) {
    final theme = Theme.of(context);
    return Card(
      child: ListTile(
        leading: Icon(icon, color: theme.colorScheme.primary),
        title: Text(title),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}

// Private provider for staff list on home screen
final _staffListProvider = FutureProvider((ref) async {
  return ref.read(apiServiceProvider).getStaffList();
});
