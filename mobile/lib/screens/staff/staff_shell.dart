import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/constants/route_names.dart';
import '../../widgets/common/adaptive_nav_scaffold.dart';

class StaffShell extends StatelessWidget {
  final Widget child;

  const StaffShell({super.key, required this.child});

  static const _routes = [
    Routes.staffHome,
    Routes.staffTasks,
    Routes.staffKpi,
    Routes.staffProfile,
  ];

  int _currentIndex(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;
    for (var i = 0; i < _routes.length; i++) {
      if (location.startsWith(_routes[i])) return i;
    }
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
          icon: Icons.person_outline,
          selectedIcon: Icons.person,
          label: 'Profil',
        ),
      ],
      child: child,
    );
  }
}
