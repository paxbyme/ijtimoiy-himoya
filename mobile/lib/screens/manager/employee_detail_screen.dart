import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../models/user_model.dart';
import '../../models/task_model.dart';
import '../../models/kpi_model.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/task_card.dart';
import '../../widgets/kpi_gauge.dart';
import '../../widgets/loading_widget.dart';

class EmployeeDetailScreen extends ConsumerWidget {
  final String employeeId;

  const EmployeeDetailScreen({super.key, required this.employeeId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final staffAsync = ref.watch(_employeeProvider(employeeId));
    final theme = Theme.of(context);

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) context.pop();
      },
      child: Scaffold(
      appBar: AppBar(
        leading: BackButton(onPressed: () => context.pop()),
        title: const Text('Employee Details'),
      ),
      body: staffAsync.when(
        loading: () => const LoadingWidget(),
        error: (error, _) => Center(child: Text('Error: $error')),
        data: (data) {
          final employee = data['employee'] as User?;
          final tasks = data['tasks'] as List<Task>;
          final kpi = data['kpi'] as KpiScore?;

          if (employee == null) {
            return const Center(child: Text('Employee not found'));
          }

          return SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Profile card
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Row(
                      children: [
                        CircleAvatar(
                          radius: 32,
                          backgroundColor: theme.colorScheme.primaryContainer,
                          child: Text(
                            employee.displayName.isNotEmpty
                                ? employee.displayName[0].toUpperCase()
                                : '?',
                            style: TextStyle(
                              fontSize: 24,
                              fontWeight: FontWeight.bold,
                              color: theme.colorScheme.onPrimaryContainer,
                            ),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                employee.displayName,
                                style: theme.textTheme.titleLarge?.copyWith(
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                employee.phone,
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Container(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(
                                  color: employee.isActive
                                      ? Colors.green.withValues(alpha: 0.1)
                                      : Colors.red.withValues(alpha: 0.1),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Text(
                                  employee.isActive ? 'Active' : 'Inactive',
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: employee.isActive
                                        ? Colors.green
                                        : Colors.red,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 20),

                // KPI Summary
                if (kpi != null) ...[
                  Text(
                    'KPI Summary',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Row(
                        children: [
                          SizedBox(
                            width: 80,
                            height: 80,
                            child: KpiGauge(score: kpi.score),
                          ),
                          const SizedBox(width: 16),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Score: ${kpi.score.toStringAsFixed(1)}',
                                style: theme.textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              if (kpi.rank != null)
                                Text(
                                  'Rank: #${kpi.rank}',
                                  style: theme.textTheme.bodyMedium,
                                ),
                              Text(
                                'Period: ${kpi.period}',
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 20),
                ],

                // Tasks
                Text(
                  'Assigned Tasks (${tasks.length})',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 8),
                if (tasks.isEmpty)
                  const Card(
                    child: Padding(
                      padding: EdgeInsets.all(16),
                      child: Center(child: Text('No tasks assigned')),
                    ),
                  )
                else
                  ...tasks.map((task) => TaskCard(task: task)),
              ],
            ),
          );
        },
      ),
      ),
    );
  }
}

final _employeeProvider =
    FutureProvider.family<Map<String, dynamic>, String>((ref, id) async {
  final api = ref.read(apiServiceProvider);

  // Fetch staff list and find the employee
  final staffList = await api.getStaffList();
  final employee = staffList.cast<User?>().firstWhere(
        (s) => s!.id == id,
        orElse: () => null,
      );

  // Fetch tasks for this employee
  final allTasks = await api.getTasks();
  final employeeTasks = allTasks.where((t) => t.assignedTo == id).toList();

  // Try to fetch KPI
  KpiScore? kpi;
  try {
    final rankings = await api.getKpiRankings();
    kpi = rankings.cast<KpiScore?>().firstWhere(
          (k) => k!.staffId == id,
          orElse: () => null,
        );
  } catch (_) {}

  return {
    'employee': employee,
    'tasks': employeeTasks,
    'kpi': kpi,
  };
});
