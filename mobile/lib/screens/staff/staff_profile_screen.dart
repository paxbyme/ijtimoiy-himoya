import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/constants/route_names.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/app_background.dart';

class StaffProfileScreen extends ConsumerWidget {
  const StaffProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userProfile = ref.watch(userProfileProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Profil')),
      body: AppBackground(child: userProfile.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => const Center(child: Text('Profilni yuklashda xatolik')),
        data: (user) {
          if (user == null) return const Center(child: Text('Profil topilmadi'));
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const SizedBox(height: 16),
              // Avatar
              Center(
                child: CircleAvatar(
                  radius: 48,
                  backgroundColor: theme.colorScheme.primaryContainer,
                  child: Text(
                    user.displayName.isNotEmpty
                        ? user.displayName[0].toUpperCase()
                        : '?',
                    style: theme.textTheme.headlineLarge?.copyWith(
                      color: theme.colorScheme.onPrimaryContainer,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Center(
                child: Text(
                  user.displayName,
                  style: theme.textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              const SizedBox(height: 32),

              // Info card
              Card(
                child: Column(
                  children: [
                    _infoTile(
                      icon: Icons.phone,
                      label: 'Telefon',
                      value: user.phone,
                    ),
                    const Divider(height: 1, indent: 56),
                    _infoTile(
                      icon: Icons.badge,
                      label: 'Xodim ID',
                      value: user.id,
                    ),
                    if (user.departmentId != null) ...[
                      const Divider(height: 1, indent: 56),
                      _infoTile(
                        icon: Icons.business,
                        label: 'Bo\'lim',
                        value: user.departmentId!,
                      ),
                    ],
                    const Divider(height: 1, indent: 56),
                    _infoTile(
                      icon: Icons.circle,
                      label: 'Holat',
                      value: user.isActive ? 'Faol' : 'Faol emas',
                      valueColor: user.isActive ? Colors.green : Colors.grey,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 32),

              // Sign out
              FilledButton.tonal(
                onPressed: () async {
                  await ref.read(authRepositoryProvider).signOut();
                  if (context.mounted) context.go(Routes.login);
                },
                style: FilledButton.styleFrom(
                  backgroundColor: theme.colorScheme.errorContainer,
                  foregroundColor: theme.colorScheme.onErrorContainer,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
                child: const Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.logout),
                    SizedBox(width: 8),
                    Text('Chiqish'),
                  ],
                ),
              ),
            ],
          );
        },
      )),
    );
  }

  Widget _infoTile({
    required IconData icon,
    required String label,
    required String value,
    Color? valueColor,
  }) {
    return ListTile(
      leading: Icon(icon, size: 20),
      title: Text(label, style: const TextStyle(fontSize: 12, color: Colors.grey)),
      subtitle: Text(
        value,
        style: TextStyle(
          fontSize: 15,
          fontWeight: FontWeight.w500,
          color: valueColor,
        ),
      ),
    );
  }
}
