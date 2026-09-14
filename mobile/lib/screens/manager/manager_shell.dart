import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/constants/route_names.dart';
import '../../widgets/common/adaptive_nav_scaffold.dart';

class ManagerShell extends StatelessWidget {
  final Widget child;

  const ManagerShell({super.key, required this.child});

  static const _routes = [
    Routes.managerHome,
    Routes.managerEmployees,
    Routes.managerTasks,
    Routes.managerKpi,
    Routes.managerChat,
  ];

  int _currentIndex(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;
    if (location.startsWith(Routes.managerHome)) return 0;
    if (location.startsWith(Routes.managerEmployees)) return 1;
    if (location.startsWith(Routes.managerTasks)) return 2;
    if (location.startsWith(Routes.managerKpi) ||
        location.startsWith(Routes.managerAiRules)) {
      return 3;
    }
    if (location.startsWith(Routes.managerChat)) return 4;
    return 0;
  }

  @override
  Widget build(BuildContext context) {
    return AdaptiveNavScaffold(
      selectedIndex: _currentIndex(context),
      onDestinationSelected: (index) => context.go(_routes[index]),
      destinations: const [
        AdaptiveNavDestination(
          icon: Icons.home_outlined,
          selectedIcon: Icons.home,
          label: 'Bosh sahifa',
        ),
        AdaptiveNavDestination(
          icon: Icons.people_outline,
          selectedIcon: Icons.people,
          label: 'Xodimlar',
        ),
        AdaptiveNavDestination(
          icon: Icons.task_alt_outlined,
          selectedIcon: Icons.task_alt,
          label: 'Topshiriqlar',
        ),
        AdaptiveNavDestination(
          icon: Icons.bar_chart_outlined,
          selectedIcon: Icons.bar_chart,
          label: 'KPI',
        ),
        AdaptiveNavDestination(
          icon: Icons.chat_outlined,
          selectedIcon: Icons.chat,
          label: 'Xabarlar',
        ),
      ],
      child: child,
    );
  }
}
