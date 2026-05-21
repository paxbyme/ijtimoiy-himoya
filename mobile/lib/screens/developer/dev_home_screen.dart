import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/constants/route_names.dart';
import '../../providers/auth_provider.dart';
import '../../providers/admin_provider.dart';
import '../../widgets/kpi/stat_card.dart';
import '../../widgets/common/app_background.dart';

class DevHomeScreen extends ConsumerWidget {
  const DevHomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userProfile = ref.watch(userProfileProvider);
    final managersAsync = ref.watch(adminManagersProvider);
    final deptsAsync = ref.watch(adminDepartmentsProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Developer Console'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'Sign out',
            onPressed: () async {
              await ref.read(authRepositoryProvider).signOut();
              if (context.mounted) context.go(Routes.login);
            },
          ),
        ],
      ),
      body: AppBackground(child: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(adminManagersProvider);
          ref.invalidate(adminDepartmentsProvider);
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
                  'Hello, ${user?.displayName ?? "Developer"}',
                  style: theme.textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                loading: () => const SizedBox.shrink(),
                error: (_, __) => const SizedBox.shrink(),
              ),
              const SizedBox(height: 4),
              Text(
                'System overview',
                style: theme.textTheme.bodyLarge?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 24),

              // Stats
              GridView.count(
                crossAxisCount: 2,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
                childAspectRatio: 1.3,
                children: [
                  managersAsync.when(
                    data: (managers) => StatCard(
                      icon: Icons.manage_accounts,
                      title: 'Managers',
                      value: '${managers.length}',
                      color: Colors.blue,
                    ),
                    loading: () => const StatCard(
                      icon: Icons.manage_accounts,
                      title: 'Managers',
                      value: '...',
                      color: Colors.blue,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.manage_accounts,
                      title: 'Managers',
                      value: '-',
                      color: Colors.blue,
                    ),
                  ),
                  deptsAsync.when(
                    data: (depts) => StatCard(
                      icon: Icons.business,
                      title: 'Departments',
                      value: '${depts.length}',
                      color: Colors.green,
                    ),
                    loading: () => const StatCard(
                      icon: Icons.business,
                      title: 'Departments',
                      value: '...',
                      color: Colors.green,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.business,
                      title: 'Departments',
                      value: '-',
                      color: Colors.green,
                    ),
                  ),
                  managersAsync.when(
                    data: (managers) {
                      final active = managers.where((m) => m.isActive).length;
                      return StatCard(
                        icon: Icons.check_circle_outline,
                        title: 'Active Managers',
                        value: '$active',
                        color: Colors.teal,
                      );
                    },
                    loading: () => const StatCard(
                      icon: Icons.check_circle_outline,
                      title: 'Active Managers',
                      value: '...',
                      color: Colors.teal,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.check_circle_outline,
                      title: 'Active Managers',
                      value: '-',
                      color: Colors.teal,
                    ),
                  ),
                  deptsAsync.when(
                    data: (depts) {
                      final assigned = depts
                          .where((d) =>
                              d.managerId != null && d.managerId!.isNotEmpty)
                          .length;
                      return StatCard(
                        icon: Icons.link,
                        title: 'Assigned Depts',
                        value: '$assigned',
                        color: Colors.orange,
                      );
                    },
                    loading: () => const StatCard(
                      icon: Icons.link,
                      title: 'Assigned Depts',
                      value: '...',
                      color: Colors.orange,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.link,
                      title: 'Assigned Depts',
                      value: '-',
                      color: Colors.orange,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Quick actions
              Text(
                'Quick Actions',
                style: theme.textTheme.titleMedium
                    ?.copyWith(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 12),
              _buildQuickAction(
                context,
                icon: Icons.person_add,
                title: 'Add Manager',
                onTap: () => context.go(Routes.developerManagers),
              ),
              _buildQuickAction(
                context,
                icon: Icons.add_business,
                title: 'Add Department',
                onTap: () => context.go(Routes.developerDepartments),
              ),
            ],
          ),
        ),
      )),
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
