import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../providers/task_provider.dart';
import '../../providers/kpi_provider.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/stat_card.dart';

class StaffHomeScreen extends ConsumerWidget {
  const StaffHomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userProfile = ref.watch(userProfileProvider);
    final tasksAsync = ref.watch(myTasksProvider);
    final kpiAsync = ref.watch(myKpiProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Bosh sahifa'),
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(myTasksProvider);
          ref.invalidate(myKpiProvider);
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
                  'Salom, ${user?.displayName ?? "Xodim"}',
                  style: theme.textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                loading: () => const SizedBox.shrink(),
                error: (_, __) => const SizedBox.shrink(),
              ),
              const SizedBox(height: 4),
              Text(
                'Bugungi umumiy ko\'rinishingiz',
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
                  tasksAsync.when(
                    data: (tasks) => StatCard(
                      icon: Icons.task_alt,
                      title: 'Topshiriqlarim',
                      value: '${tasks.length}',
                      color: Colors.blue,
                    ),
                    loading: () => const StatCard(
                      icon: Icons.task_alt,
                      title: 'Topshiriqlarim',
                      value: '...',
                      color: Colors.blue,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.task_alt,
                      title: 'Topshiriqlarim',
                      value: '-',
                      color: Colors.blue,
                    ),
                  ),
                  tasksAsync.when(
                    data: (tasks) {
                      final pending =
                          tasks.where((t) => t.status == 'PENDING').length;
                      return StatCard(
                        icon: Icons.pending_actions,
                        title: 'Kutilmoqda',
                        value: '$pending',
                        color: Colors.orange,
                      );
                    },
                    loading: () => const StatCard(
                      icon: Icons.pending_actions,
                      title: 'Kutilmoqda',
                      value: '...',
                      color: Colors.orange,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.pending_actions,
                      title: 'Kutilmoqda',
                      value: '-',
                      color: Colors.orange,
                    ),
                  ),
                  tasksAsync.when(
                    data: (tasks) {
                      final completed =
                          tasks.where((t) => t.status == 'COMPLETED').length;
                      return StatCard(
                        icon: Icons.check_circle_outline,
                        title: 'Bajarildi',
                        value: '$completed',
                        color: Colors.green,
                      );
                    },
                    loading: () => const StatCard(
                      icon: Icons.check_circle_outline,
                      title: 'Bajarildi',
                      value: '...',
                      color: Colors.green,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.check_circle_outline,
                      title: 'Bajarildi',
                      value: '-',
                      color: Colors.green,
                    ),
                  ),
                  kpiAsync.when(
                    data: (kpi) => StatCard(
                      icon: Icons.bar_chart,
                      title: 'Mening KPI',
                      value:
                          kpi != null ? kpi.score.toStringAsFixed(1) : 'N/A',
                      color: Colors.purple,
                    ),
                    loading: () => const StatCard(
                      icon: Icons.bar_chart,
                      title: 'Mening KPI',
                      value: '...',
                      color: Colors.purple,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.bar_chart,
                      title: 'Mening KPI',
                      value: '-',
                      color: Colors.purple,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // AI Assistant card
              _AiAssistantCard(),
              const SizedBox(height: 24),

              // Quick actions
              Text(
                'Tezkor amallar',
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 12),
              _buildQuickAction(
                context,
                icon: Icons.bar_chart,
                title: 'KPI ko\'rish',
                onTap: () => context.go('/staff/kpi'),
              ),
              _buildQuickAction(
                context,
                icon: Icons.person,
                title: 'Mening profilim',
                onTap: () => context.go('/staff/profile'),
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

class _AiAssistantCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return GestureDetector(
      onTap: () => context.go('/staff/ai-chat'),
      child: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [
              theme.colorScheme.primary,
              theme.colorScheme.primary.withValues(alpha: 0.75),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(16),
        ),
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(Icons.smart_toy, color: Colors.white, size: 28),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'AI Yordamchi',
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'Savol bering, tarix ko\'ring',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: Colors.white.withValues(alpha: 0.85),
                    ),
                  ),
                ],
              ),
            ),
            Icon(
              Icons.arrow_forward_ios,
              color: Colors.white.withValues(alpha: 0.8),
              size: 18,
            ),
          ],
        ),
      ),
    );
  }
}
