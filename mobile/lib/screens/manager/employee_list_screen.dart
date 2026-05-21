import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/constants/route_names.dart';
import '../../models/auth/user_model.dart';
import '../../providers/admin_provider.dart';
import '../../providers/auth_provider.dart';
import '../../providers/chat_provider.dart';
import '../../widgets/common/loading_widget.dart';
import '../../widgets/common/empty_state_widget.dart';
import '../../widgets/common/app_background.dart';

final staffListProvider = StreamProvider<List<User>>((ref) {
  final userAsync = ref.watch(userProfileProvider);
  return userAsync.when(
    data: (user) {
      final departmentId = user?.departmentId;
      if (departmentId == null) return Stream.value([]);
      return ref.read(chatRepositoryProvider).staffStream(departmentId);
    },
    loading: () => Stream.value([]),
    error: (_, __) => Stream.value([]),
  );
});

class EmployeeListScreen extends ConsumerWidget {
  const EmployeeListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final staffAsync = ref.watch(staffListProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Xodimlar'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'Chiqish',
            onPressed: () async {
              await ref.read(authRepositoryProvider).signOut();
              if (context.mounted) context.go(Routes.login);
            },
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showAddEmployeeSheet(context, ref),
        child: const Icon(Icons.person_add),
      ),
      body: AppBackground(child: staffAsync.when(
        loading: () => const LoadingWidget(),
        error: (error, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Xodimlarni yuklashda xatolik',
                  style: TextStyle(color: theme.colorScheme.error)),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => ref.invalidate(staffListProvider),
                child: const Text('Qayta urinish'),
              ),
            ],
          ),
        ),
        data: (staff) {
          if (staff.isEmpty) {
            return const EmptyStateWidget(
              icon: Icons.people_outline,
              message: 'Hali xodimlar yo\'q. + tugmasini bosing.',
            );
          }

          return RefreshIndicator(
            onRefresh: () async {},
            child: ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: staff.length,
              itemBuilder: (context, index) {
                final employee = staff[index];
                return Card(
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor: theme.colorScheme.primaryContainer,
                      child: Text(
                        employee.displayName.isNotEmpty
                            ? employee.displayName[0].toUpperCase()
                            : '?',
                        style: TextStyle(
                          color: theme.colorScheme.onPrimaryContainer,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    title: Text(
                      employee.displayName,
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    subtitle: Text(employee.phone),
                    trailing: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: employee.isActive
                            ? Colors.green.withValues(alpha: 0.1)
                            : Colors.red.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(
                        employee.isActive ? 'Faol' : 'Faol emas',
                        style: TextStyle(
                          fontSize: 12,
                          color: employee.isActive ? Colors.green : Colors.red,
                        ),
                      ),
                    ),
                    onTap: () =>
                        context.push(Routes.managerEmployeeDetail(employee.id)),
                  ),
                );
              },
            ),
          );
        },
      )),
    );
  }

  void _showAddEmployeeSheet(BuildContext context, WidgetRef ref) {
    final formKey = GlobalKey<FormState>();
    final nameController = TextEditingController();
    final phoneController = TextEditingController();
    final passwordController = TextEditingController();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => Padding(
        padding: EdgeInsets.fromLTRB(
          24,
          24,
          24,
          24 + MediaQuery.of(ctx).viewInsets.bottom,
        ),
        child: Form(
          key: formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Xodim qo\'shish',
                style: Theme.of(ctx).textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const SizedBox(height: 20),
              TextFormField(
                controller: nameController,
                decoration: const InputDecoration(
                  labelText: 'To\'liq ism',
                  prefixIcon: Icon(Icons.person_outline),
                ),
                validator: (v) =>
                    v == null || v.trim().isEmpty ? 'Ism talab qilinadi' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: phoneController,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(
                  labelText: 'Telefon raqami',
                  prefixIcon: Icon(Icons.phone_outlined),
                ),
                validator: (v) => v == null || v.trim().isEmpty
                    ? 'Telefon talab qilinadi'
                    : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: passwordController,
                obscureText: true,
                decoration: const InputDecoration(
                  labelText: 'Parol',
                  prefixIcon: Icon(Icons.lock_outlined),
                ),
                validator: (v) {
                  if (v == null || v.isEmpty) return 'Parol talab qilinadi';
                  if (v.length < 8) return 'Kamida 8 ta belgi';
                  return null;
                },
              ),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: () async {
                  if (!formKey.currentState!.validate()) return;

                  final result =
                      await ref.read(adminRepositoryProvider).createStaff({
                    'displayName': nameController.text.trim(),
                    'phone': phoneController.text.trim(),
                    'password': passwordController.text,
                  });
                  result.fold(
                    (failure) {
                      if (ctx.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(
                                'Xodim qo\'shishda xatolik: ${failure.message}'),
                            backgroundColor:
                                Theme.of(context).colorScheme.error,
                          ),
                        );
                      }
                    },
                    (_) {
                      if (ctx.mounted) Navigator.pop(ctx);
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Xodim qo\'shildi')),
                        );
                      }
                    },
                  );
                },
                child: const Text('Xodim qo\'shish'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
