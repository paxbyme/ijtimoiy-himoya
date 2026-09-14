import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/constants/route_names.dart';
import '../../widgets/common/adaptive_nav_scaffold.dart';

class DeveloperShell extends StatelessWidget {
  final Widget child;

  const DeveloperShell({super.key, required this.child});

  static const _routes = [
    Routes.developerHome,
    Routes.developerManagers,
    Routes.developerDepartments,
  ];

  int _currentIndex(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;
    if (location.startsWith(Routes.developerManagers)) return 1;
    if (location.startsWith(Routes.developerDepartments)) return 2;
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
          label: 'Home',
        ),
        AdaptiveNavDestination(
          icon: Icons.manage_accounts_outlined,
          selectedIcon: Icons.manage_accounts,
          label: 'Managers',
        ),
        AdaptiveNavDestination(
          icon: Icons.business_outlined,
          selectedIcon: Icons.business,
          label: 'Departments',
        ),
      ],
      child: child,
    );
  }
}
