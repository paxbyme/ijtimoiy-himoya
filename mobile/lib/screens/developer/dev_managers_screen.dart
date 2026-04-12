import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../models/department_model.dart';
import '../../models/manager_stats_model.dart';
import '../../models/user_model.dart';
import '../../providers/auth_provider.dart';
import '../../providers/admin_provider.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';

String _extractError(Object e) {
  if (e is DioException) {
    final data = e.response?.data;
    if (data is Map) {
      final msg = data['message'] as String?;
      final errors = data['data'];
      if (errors is Map && errors.isNotEmpty) {
        return errors.values.first.toString();
      }
      if (msg != null && msg.isNotEmpty) return msg;
    }
    return 'Server error (${e.response?.statusCode ?? 'no response'})';
  }
  return e.toString();
}

class DevManagersScreen extends ConsumerWidget {
  const DevManagersScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final managersAsync = ref.watch(adminManagersProvider);
    final deptsAsync = ref.watch(adminDepartmentsProvider);
    final theme = Theme.of(context);

    final deptMap = deptsAsync.maybeWhen(
      data: (depts) => {for (final d in depts) d.id: d.name},
      orElse: () => <String, String>{},
    );

    return Scaffold(
      appBar: AppBar(
        title: const Text('Managers'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(adminManagersProvider),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showAddManagerSheet(
          context,
          ref,
          deptsAsync.value ?? [],
        ),
        child: const Icon(Icons.person_add),
      ),
      body: managersAsync.when(
        loading: () => const LoadingWidget(),
        error: (e, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.error_outline, color: theme.colorScheme.error, size: 40),
              const SizedBox(height: 12),
              Text('Failed to load managers',
                  style: TextStyle(
                      color: theme.colorScheme.error,
                      fontWeight: FontWeight.w600)),
              const SizedBox(height: 6),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 32),
                child: Text(e.toString(),
                    textAlign: TextAlign.center,
                    style: TextStyle(
                        fontSize: 12,
                        color: theme.colorScheme.onSurfaceVariant)),
              ),
              const SizedBox(height: 12),
              TextButton(
                onPressed: () => ref.invalidate(adminManagersProvider),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
        data: (managers) {
          if (managers.isEmpty) {
            return const EmptyStateWidget(
              icon: Icons.manage_accounts_outlined,
              message: 'No managers yet. Tap + to create one.',
            );
          }
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(adminManagersProvider),
            child: ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: managers.length,
              itemBuilder: (context, index) {
                final m = managers[index];
                final deptName = m.departmentId != null &&
                        m.departmentId!.isNotEmpty
                    ? deptMap[m.departmentId!]
                    : null;
                return Card(
                  child: InkWell(
                    borderRadius: BorderRadius.circular(12),
                    onTap: () => _showStats(context, ref, m),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 4, vertical: 4),
                      child: ListTile(
                        leading: CircleAvatar(
                          backgroundColor: m.isActive
                              ? theme.colorScheme.primaryContainer
                              : theme.colorScheme.surfaceContainerHighest,
                          child: Text(
                            m.displayName.isNotEmpty
                                ? m.displayName[0].toUpperCase()
                                : '?',
                            style: TextStyle(
                              color: m.isActive
                                  ? theme.colorScheme.onPrimaryContainer
                                  : theme.colorScheme.onSurfaceVariant,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        title: Row(
                          children: [
                            Expanded(
                              child: Text(m.displayName,
                                  style: const TextStyle(
                                      fontWeight: FontWeight.w600)),
                            ),
                            if (!m.isActive)
                              Container(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: Colors.red.withValues(alpha: 0.1),
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: const Text('Inactive',
                                    style: TextStyle(
                                        fontSize: 10, color: Colors.red)),
                              ),
                          ],
                        ),
                        subtitle: Text(
                          '${m.phone}${deptName != null ? '  ·  $deptName' : ''}',
                          style: const TextStyle(fontSize: 13),
                        ),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton(
                              icon: const Icon(Icons.bar_chart_outlined),
                              tooltip: 'Analytics',
                              onPressed: () => _showStats(context, ref, m),
                            ),
                            if (m.isActive)
                              IconButton(
                                icon: const Icon(Icons.person_off_outlined,
                                    color: Colors.orange),
                                tooltip: 'Deactivate',
                                onPressed: () => _confirmDeactivate(
                                    context, ref, m.id, m.displayName),
                              ),
                            IconButton(
                              icon: Icon(Icons.delete_forever_outlined,
                                  color: theme.colorScheme.error),
                              tooltip: 'Delete permanently',
                              onPressed: () => _confirmHardDelete(
                                  context, ref, m.id, m.displayName),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }

  void _showStats(BuildContext context, WidgetRef ref, User manager) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _StatsSheet(manager: manager, ref: ref),
    );
  }

  void _confirmDeactivate(
      BuildContext context, WidgetRef ref, String id, String name) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Deactivate Manager'),
        content:
            Text('Deactivate $name? They will no longer be able to sign in.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            style: TextButton.styleFrom(foregroundColor: Colors.orange),
            onPressed: () async {
              Navigator.pop(ctx);
              try {
                await ref.read(apiServiceProvider).deactivateManager(id);
                ref.invalidate(adminManagersProvider);
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Manager deactivated')),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                    content: Text('Failed: ${_extractError(e)}'),
                    backgroundColor: Theme.of(context).colorScheme.error,
                  ));
                }
              }
            },
            child: const Text('Deactivate'),
          ),
        ],
      ),
    );
  }

  void _confirmHardDelete(
      BuildContext context, WidgetRef ref, String id, String name) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Permanently'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Permanently delete "$name"?'),
            const SizedBox(height: 8),
            Text(
              '• Firebase Auth account deleted\n'
              '• Firestore document deleted\n'
              '• Removed from all departments\n\n'
              'This CANNOT be undone.',
              style: TextStyle(fontSize: 13, color: Colors.red.shade700),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            onPressed: () async {
              Navigator.pop(ctx);
              try {
                await ref.read(apiServiceProvider).hardDeleteManager(id);
                ref.invalidate(adminManagersProvider);
                ref.invalidate(adminDepartmentsProvider);
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                        content: Text('Manager permanently deleted')),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                    content: Text('Failed: ${_extractError(e)}'),
                    backgroundColor: Theme.of(context).colorScheme.error,
                  ));
                }
              }
            },
            child: const Text('Delete Permanently'),
          ),
        ],
      ),
    );
  }

  void _showAddManagerSheet(
      BuildContext context, WidgetRef ref, List<Department> depts) {
    final formKey = GlobalKey<FormState>();
    final nameController = TextEditingController();
    final phoneController = TextEditingController();
    final passwordController = TextEditingController();
    String? selectedDeptId;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setState) => Padding(
          padding: EdgeInsets.fromLTRB(
              24, 24, 24, 24 + MediaQuery.of(ctx).viewInsets.bottom),
          child: Form(
            key: formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'Add Manager',
                  style: Theme.of(ctx)
                      .textTheme
                      .titleLarge
                      ?.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 20),
                TextFormField(
                  controller: nameController,
                  decoration: const InputDecoration(
                    labelText: 'Full Name',
                    prefixIcon: Icon(Icons.person_outline),
                  ),
                  validator: (v) =>
                      v == null || v.trim().isEmpty ? 'Name is required' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: phoneController,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(
                    labelText: 'Phone Number',
                    prefixIcon: Icon(Icons.phone_outlined),
                  ),
                  validator: (v) => v == null || v.trim().isEmpty
                      ? 'Phone is required'
                      : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: passwordController,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'Password (min 8 chars)',
                    prefixIcon: Icon(Icons.lock_outlined),
                  ),
                  validator: (v) {
                    if (v == null || v.isEmpty) return 'Password is required';
                    if (v.length < 8) return 'Min 8 characters';
                    return null;
                  },
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  initialValue: selectedDeptId,
                  decoration: const InputDecoration(
                    labelText: 'Department (optional)',
                  ),
                  items: [
                    const DropdownMenuItem<String>(
                        value: null, child: Text('— Assign later —')),
                    ...depts.map(
                      (d) => DropdownMenuItem<String>(
                          value: d.id, child: Text(d.name)),
                    ),
                  ],
                  onChanged: (v) => setState(() => selectedDeptId = v),
                ),
                const SizedBox(height: 24),
                ElevatedButton(
                  onPressed: () async {
                    if (!formKey.currentState!.validate()) return;
                    try {
                      await ref.read(apiServiceProvider).createManager({
                        'displayName': nameController.text.trim(),
                        'phone': phoneController.text.trim(),
                        'password': passwordController.text,
                        if (selectedDeptId != null)
                          'departmentId': selectedDeptId,
                      });
                      ref.invalidate(adminManagersProvider);
                      if (ctx.mounted) Navigator.pop(ctx);
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                              content: Text('Manager created successfully')),
                        );
                      }
                    } catch (e) {
                      if (ctx.mounted) {
                        ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(
                          content: Text('Failed: ${_extractError(e)}'),
                          backgroundColor: Theme.of(ctx).colorScheme.error,
                        ));
                      }
                    }
                  },
                  child: const Text('Add Manager'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ── Stats bottom sheet ──

class _StatsSheet extends StatefulWidget {
  final User manager;
  final WidgetRef ref;
  const _StatsSheet({required this.manager, required this.ref});

  @override
  State<_StatsSheet> createState() => _StatsSheetState();
}

class _StatsSheetState extends State<_StatsSheet> {
  ManagerStats? _stats;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final stats = await widget.ref
          .read(apiServiceProvider)
          .getManagerStats(widget.manager.id);
      if (mounted) setState(() { _stats = stats; _loading = false; });
    } catch (e) {
      if (mounted) setState(() { _error = _extractError(e); _loading = false; });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final m = widget.manager;

    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.75,
      maxChildSize: 0.95,
      builder: (_, scroll) => Padding(
        padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 36,
                height: 4,
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: theme.colorScheme.outlineVariant,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            // Header
            Row(
              children: [
                CircleAvatar(
                  radius: 24,
                  backgroundColor: m.isActive
                      ? theme.colorScheme.primaryContainer
                      : theme.colorScheme.surfaceContainerHighest,
                  child: Text(
                    m.displayName.isNotEmpty
                        ? m.displayName[0].toUpperCase()
                        : '?',
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: m.isActive
                          ? theme.colorScheme.onPrimaryContainer
                          : theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(m.displayName,
                          style: theme.textTheme.titleMedium
                              ?.copyWith(fontWeight: FontWeight.bold)),
                      Text(m.phone,
                          style: TextStyle(
                              fontSize: 13,
                              color: theme.colorScheme.onSurfaceVariant)),
                    ],
                  ),
                ),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: m.isActive
                        ? Colors.green.withValues(alpha: 0.12)
                        : Colors.red.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    m.isActive ? 'Active' : 'Inactive',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color:
                          m.isActive ? Colors.green.shade700 : Colors.red,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            if (_loading)
              const Expanded(
                  child: Center(child: CircularProgressIndicator()))
            else if (_error != null)
              Expanded(
                child: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.error_outline,
                          color: theme.colorScheme.error, size: 36),
                      const SizedBox(height: 8),
                      Text(_error!,
                          textAlign: TextAlign.center,
                          style: TextStyle(
                              color: theme.colorScheme.error)),
                      const SizedBox(height: 12),
                      TextButton(
                          onPressed: () {
                            setState(() {
                              _loading = true;
                              _error = null;
                            });
                            _load();
                          },
                          child: const Text('Retry')),
                    ],
                  ),
                ),
              )
            else
              Expanded(
                child: ListView(
                  controller: scroll,
                  children: [
                    if (_stats!.departmentName != null)
                      _InfoRow(
                          icon: Icons.business_outlined,
                          label: 'Department',
                          value: _stats!.departmentName!),
                    if (_stats!.currentPeriod != null)
                      _InfoRow(
                          icon: Icons.calendar_month_outlined,
                          label: 'Period',
                          value: _stats!.currentPeriod!),
                    const SizedBox(height: 16),
                    _SectionTitle('Staff'),
                    const SizedBox(height: 8),
                    Row(children: [
                      Expanded(
                          child: _StatCard(
                              label: 'Total',
                              value: '${_stats!.staffTotal}',
                              color: theme.colorScheme.primary)),
                      const SizedBox(width: 10),
                      Expanded(
                          child: _StatCard(
                              label: 'Active',
                              value: '${_stats!.staffActive}',
                              color: Colors.green)),
                      const SizedBox(width: 10),
                      Expanded(
                          child: _StatCard(
                              label: 'Inactive',
                              value:
                                  '${_stats!.staffTotal - _stats!.staffActive}',
                              color: Colors.grey)),
                    ]),
                    const SizedBox(height: 16),
                    _SectionTitle('Tasks (Department)'),
                    const SizedBox(height: 8),
                    Row(children: [
                      Expanded(
                          child: _StatCard(
                              label: 'Total',
                              value: '${_stats!.taskTotal}',
                              color: theme.colorScheme.secondary)),
                      const SizedBox(width: 10),
                      Expanded(
                          child: _StatCard(
                              label: 'Done',
                              value: '${_stats!.taskCompleted}',
                              color: Colors.green)),
                      const SizedBox(width: 10),
                      Expanded(
                          child: _StatCard(
                              label: 'Active',
                              value: '${_stats!.taskInProgress}',
                              color: Colors.blue)),
                    ]),
                    const SizedBox(height: 8),
                    Row(children: [
                      Expanded(
                          child: _StatCard(
                              label: 'Pending',
                              value: '${_stats!.taskPending}',
                              color: Colors.orange)),
                      const SizedBox(width: 10),
                      Expanded(
                          child: _StatCard(
                              label: 'Cancelled',
                              value: '${_stats!.taskCancelled}',
                              color: Colors.red)),
                      const SizedBox(width: 10),
                      Expanded(
                          child: _StatCard(
                              label: 'Rate',
                              value: _stats!.taskTotal > 0
                                  ? '${(_stats!.taskCompleted * 100 / _stats!.taskTotal).round()}%'
                                  : '—',
                              color: Colors.teal)),
                    ]),
                    const SizedBox(height: 16),
                    _SectionTitle('KPI (avg this month)'),
                    const SizedBox(height: 8),
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Row(
                        children: [
                          Icon(
                              Icons.emoji_events_outlined,
                              color: _stats!.avgKpiScore != null
                                  ? Colors.amber
                                  : theme.colorScheme.outlineVariant,
                              size: 32),
                          const SizedBox(width: 12),
                          Text(
                            _stats!.avgKpiScore != null
                                ? '${_stats!.avgKpiScore!.toStringAsFixed(1)} / 100'
                                : 'No KPI data yet',
                            style: theme.textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: _stats!.avgKpiScore != null
                                  ? theme.colorScheme.onSurface
                                  : theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 8),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);
  @override
  Widget build(BuildContext context) => Text(text,
      style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: Theme.of(context).colorScheme.onSurfaceVariant));
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  const _InfoRow(
      {required this.icon, required this.label, required this.value});
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        children: [
          Icon(icon, size: 18, color: theme.colorScheme.onSurfaceVariant),
          const SizedBox(width: 8),
          Text('$label: ',
              style: TextStyle(
                  fontSize: 13,
                  color: theme.colorScheme.onSurfaceVariant)),
          Expanded(
            child: Text(value,
                style: const TextStyle(
                    fontSize: 13, fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  final String label;
  final String value;
  final Color color;
  const _StatCard(
      {required this.label, required this.value, required this.color});
  @override
  Widget build(BuildContext context) => Container(
        padding:
            const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: color.withValues(alpha: 0.25)),
        ),
        child: Column(
          children: [
            Text(value,
                style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: color)),
            const SizedBox(height: 2),
            Text(label,
                style: TextStyle(
                    fontSize: 11,
                    color: color.withValues(alpha: 0.8))),
          ],
        ),
      );
}
