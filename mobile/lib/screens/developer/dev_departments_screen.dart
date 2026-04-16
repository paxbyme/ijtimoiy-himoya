import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../models/department_model.dart';
import '../../models/user_model.dart';
import '../../providers/auth_provider.dart';
import '../../providers/admin_provider.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';
import '../../widgets/app_background.dart';

class DevDepartmentsScreen extends ConsumerWidget {
  const DevDepartmentsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final deptsAsync = ref.watch(adminDepartmentsProvider);
    final managersAsync = ref.watch(adminManagersProvider);
    final theme = Theme.of(context);

    final managerMap = managersAsync.maybeWhen(
      data: (managers) => {for (final m in managers) m.id: m.displayName},
      orElse: () => <String, String>{},
    );
    final activeManagers = managersAsync.maybeWhen(
      data: (managers) => managers.where((m) => m.isActive).toList(),
      orElse: () => <User>[],
    );

    return Scaffold(
      appBar: AppBar(
        title: const Text('Departments'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(adminDepartmentsProvider),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () =>
            _showDeptSheet(context, ref, activeManagers, null),
        child: const Icon(Icons.add_business),
      ),
      body: AppBackground(child: deptsAsync.when(
        loading: () => const LoadingWidget(),
        error: (e, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Failed to load departments',
                  style: TextStyle(color: theme.colorScheme.error)),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => ref.invalidate(adminDepartmentsProvider),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
        data: (depts) {
          if (depts.isEmpty) {
            return const EmptyStateWidget(
              icon: Icons.business_outlined,
              message: 'No departments yet. Tap + to create one.',
            );
          }
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(adminDepartmentsProvider),
            child: ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: depts.length,
              itemBuilder: (context, index) {
                final dept = depts[index];
                final managerName = dept.managerId != null &&
                        dept.managerId!.isNotEmpty
                    ? managerMap[dept.managerId!]
                    : null;
                return Card(
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor:
                          theme.colorScheme.secondaryContainer,
                      child: Icon(Icons.business,
                          color: theme.colorScheme.onSecondaryContainer,
                          size: 20),
                    ),
                    title: Text(dept.name,
                        style:
                            const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: Text(
                      managerName != null
                          ? 'Manager: $managerName'
                          : 'No manager assigned',
                      style: TextStyle(
                        fontSize: 13,
                        color: managerName != null
                            ? null
                            : theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.edit_outlined),
                          tooltip: 'Edit',
                          onPressed: () => _showDeptSheet(
                              context, ref, activeManagers, dept),
                        ),
                        IconButton(
                          icon: Icon(Icons.delete_outline,
                              color: theme.colorScheme.error),
                          tooltip: 'Delete',
                          onPressed: () => _confirmDelete(
                              context, ref, dept.id, dept.name),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          );
        },
      )),
    );
  }

  void _confirmDelete(
      BuildContext context, WidgetRef ref, String id, String name) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Department'),
        content: Text('Delete "$name"? This cannot be undone.'),
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
                await ref.read(apiServiceProvider).deleteDepartment(id);
                ref.invalidate(adminDepartmentsProvider);
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Department deleted')),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  // Show backend guard message (e.g. "Cannot delete department with active staff")
                  final msg = e.toString().contains('Cannot delete')
                      ? e.toString().replaceAll('Exception: ', '')
                      : 'Failed to delete department';
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(msg),
                      backgroundColor: Theme.of(context).colorScheme.error,
                    ),
                  );
                }
              }
            },
            child: const Text('Delete'),
          ),
        ],
      ),
    );
  }

  void _showDeptSheet(BuildContext context, WidgetRef ref,
      List<User> managers, Department? existing) {
    final formKey = GlobalKey<FormState>();
    final nameController =
        TextEditingController(text: existing?.name ?? '');
    String? selectedManagerId = existing?.managerId?.isNotEmpty == true
        ? existing!.managerId
        : null;

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
                  existing == null
                      ? 'Add Department'
                      : 'Edit Department',
                  style: Theme.of(ctx)
                      .textTheme
                      .titleLarge
                      ?.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 20),
                TextFormField(
                  controller: nameController,
                  decoration: const InputDecoration(
                    labelText: 'Department Name',
                    prefixIcon: Icon(Icons.business_outlined),
                  ),
                  validator: (v) =>
                      v == null || v.trim().isEmpty ? 'Name is required' : null,
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  initialValue: selectedManagerId,
                  decoration: const InputDecoration(
                    labelText: 'Manager (optional)',
                  ),
                  items: [
                    const DropdownMenuItem<String>(
                        value: null, child: Text('— None —')),
                    ...managers.map(
                      (m) => DropdownMenuItem<String>(
                          value: m.id, child: Text(m.displayName)),
                    ),
                  ],
                  onChanged: (v) => setState(() => selectedManagerId = v),
                ),
                const SizedBox(height: 24),
                ElevatedButton(
                  onPressed: () async {
                    if (!formKey.currentState!.validate()) return;
                    try {
                      final payload = {
                        'name': nameController.text.trim(),
                        if (selectedManagerId != null)
                          'managerId': selectedManagerId,
                      };
                      if (existing == null) {
                        await ref
                            .read(apiServiceProvider)
                            .createDepartment(payload);
                      } else {
                        await ref
                            .read(apiServiceProvider)
                            .updateDepartment(existing.id, payload);
                      }
                      ref.invalidate(adminDepartmentsProvider);
                      ref.invalidate(adminManagersProvider);
                      if (ctx.mounted) Navigator.pop(ctx);
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(existing == null
                                ? 'Department created'
                                : 'Department updated'),
                          ),
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
                  child: Text(existing == null ? 'Create' : 'Save Changes'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
