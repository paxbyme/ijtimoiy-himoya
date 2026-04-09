import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../providers/task_provider.dart';
import '../../providers/kpi_provider.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/stat_card.dart';
import '../../widgets/ai_chat_sheet.dart';

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
        title: const Text('Home'),
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
                  'Hello, ${user?.displayName ?? "Staff"}',
                  style: theme.textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                loading: () => const SizedBox.shrink(),
                error: (_, __) => const SizedBox.shrink(),
              ),
              const SizedBox(height: 4),
              Text(
                'Here\'s your overview for today',
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
                      title: 'My Tasks',
                      value: '${tasks.length}',
                      color: Colors.blue,
                    ),
                    loading: () => const StatCard(
                      icon: Icons.task_alt,
                      title: 'My Tasks',
                      value: '...',
                      color: Colors.blue,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.task_alt,
                      title: 'My Tasks',
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
                  tasksAsync.when(
                    data: (tasks) {
                      final completed =
                          tasks.where((t) => t.status == 'COMPLETED').length;
                      return StatCard(
                        icon: Icons.check_circle_outline,
                        title: 'Completed',
                        value: '$completed',
                        color: Colors.green,
                      );
                    },
                    loading: () => const StatCard(
                      icon: Icons.check_circle_outline,
                      title: 'Completed',
                      value: '...',
                      color: Colors.green,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.check_circle_outline,
                      title: 'Completed',
                      value: '-',
                      color: Colors.green,
                    ),
                  ),
                  kpiAsync.when(
                    data: (kpi) => StatCard(
                      icon: Icons.bar_chart,
                      title: 'My KPI',
                      value:
                          kpi != null ? kpi.score.toStringAsFixed(1) : 'N/A',
                      color: Colors.purple,
                    ),
                    loading: () => const StatCard(
                      icon: Icons.bar_chart,
                      title: 'My KPI',
                      value: '...',
                      color: Colors.purple,
                    ),
                    error: (_, __) => const StatCard(
                      icon: Icons.bar_chart,
                      title: 'My KPI',
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
                'Quick Actions',
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 12),
              _buildQuickAction(
                context,
                icon: Icons.bar_chart,
                title: 'View My KPIs',
                onTap: () => context.go('/staff/kpi'),
              ),
              _buildQuickAction(
                context,
                icon: Icons.person,
                title: 'My Profile',
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

class _AiAssistantCard extends StatefulWidget {
  @override
  State<_AiAssistantCard> createState() => _AiAssistantCardState();
}

class _AiAssistantCardState extends State<_AiAssistantCard> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _open({String? message}) {
    showAiChatSheet(context, initialMessage: message);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
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
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.smart_toy, color: Colors.white, size: 22),
              const SizedBox(width: 8),
              Text(
                'AI Assistant',
                style: theme.textTheme.titleMedium?.copyWith(
                  color: Colors.white,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            'Ask about staff, roles, departments & policies',
            style: theme.textTheme.bodySmall?.copyWith(
              color: Colors.white.withValues(alpha: 0.85),
            ),
          ),
          const SizedBox(height: 12),
          // Inline input
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _controller,
                  textInputAction: TextInputAction.send,
                  onSubmitted: (value) {
                    final text = value.trim();
                    _controller.clear();
                    if (text.isNotEmpty) {
                      _open(message: text);
                    } else {
                      _open();
                    }
                  },
                  style: const TextStyle(color: Colors.white),
                  decoration: InputDecoration(
                    hintText: 'Ask a question...',
                    hintStyle: TextStyle(
                      color: Colors.white.withValues(alpha: 0.6),
                    ),
                    filled: true,
                    fillColor: Colors.white.withValues(alpha: 0.15),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(24),
                      borderSide: BorderSide.none,
                    ),
                    contentPadding: const EdgeInsets.symmetric(
                        horizontal: 16, vertical: 10),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              IconButton(
                onPressed: () {
                  final text = _controller.text.trim();
                  _controller.clear();
                  _open(message: text.isNotEmpty ? text : null);
                },
                icon: const Icon(Icons.arrow_forward, color: Colors.white),
                style: IconButton.styleFrom(
                  backgroundColor: Colors.white.withValues(alpha: 0.2),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
