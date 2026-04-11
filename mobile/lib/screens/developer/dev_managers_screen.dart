import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../models/department_model.dart';
import '../../providers/auth_provider.dart';
import '../../providers/admin_provider.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';

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
              Text('Failed to load managers',
                  style: TextStyle(color: theme.colorScheme.error)),
              const SizedBox(height: 8),
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
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor: theme.colorScheme.primaryContainer,
                      child: Text(
                        m.displayName.isNotEmpty
                            ? m.displayName[0].toUpperCase()
                            : '?',
                        style: TextStyle(
                          color: theme.colorScheme.onPrimaryContainer,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    title: Text(m.displayName,
                        style:
                            const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: Text(
                      '${m.phone}${deptName != null ? '  ·  $deptName' : ''}',
                      style: const TextStyle(fontSize: 13),
                    ),
                    trailing: m.isActive
                        ? IconButton(
                            icon: const Icon(Icons.person_off_outlined,
                                color: Colors.red),
                            tooltip: 'Deactivate',
                            onPressed: () => _confirmDeactivate(
                                context, ref, m.id, m.displayName),
                          )
                        : Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 8, vertical: 4),
                            decoration: BoxDecoration(
                              color: Colors.red.withValues(alpha: 0.1),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: const Text('Inactive',
                                style: TextStyle(
                                    fontSize: 12, color: Colors.red)),
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

  void _confirmDeactivate(
      BuildContext context, WidgetRef ref, String id, String name) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Deactivate Manager'),
        content: Text('Deactivate $name? They will no longer be able to sign in.'),
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
                await ref.read(apiServiceProvider).deactivateManager(id);
                ref.invalidate(adminManagersProvider);
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Manager deactivated')),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('Failed: $e'),
                      backgroundColor:
                          Theme.of(context).colorScheme.error,
                    ),
                  );
                }
              }
            },
            child: const Text('Deactivate'),
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
                        ScaffoldMessenger.of(ctx).showSnackBar(
                          SnackBar(
                            content: Text('Failed: $e'),
                            backgroundColor:
                                Theme.of(ctx).colorScheme.error,
                          ),
                        );
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
