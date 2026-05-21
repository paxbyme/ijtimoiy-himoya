import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/constants/route_names.dart';

class ManagerShell extends StatelessWidget {
  final Widget child;

  const ManagerShell({super.key, required this.child});

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

  void _onTap(BuildContext context, int index) {
    switch (index) {
      case 0:
        context.go(Routes.managerHome);
        break;
      case 1:
        context.go(Routes.managerEmployees);
        break;
      case 2:
        context.go(Routes.managerTasks);
        break;
      case 3:
        context.go(Routes.managerKpi);
        break;
      case 4:
        context.go(Routes.managerChat);
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          // SJMA watermark
          Positioned.fill(
            child: IgnorePointer(
              child: Center(
                child: Opacity(
                  opacity: 0.22,
                  child: Image.asset(
                    'assets/images/logo.png',
                    width: 320,
                    height: 320,
                  ),
                ),
              ),
            ),
          ),
          child,
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex(context),
        onDestinationSelected: (index) => _onTap(context, index),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home),
            label: 'Bosh sahifa',
          ),
          NavigationDestination(
            icon: Icon(Icons.people_outline),
            selectedIcon: Icon(Icons.people),
            label: 'Xodimlar',
          ),
          NavigationDestination(
            icon: Icon(Icons.task_alt_outlined),
            selectedIcon: Icon(Icons.task_alt),
            label: 'Topshiriqlar',
          ),
          NavigationDestination(
            icon: Icon(Icons.bar_chart_outlined),
            selectedIcon: Icon(Icons.bar_chart),
            label: 'KPI',
          ),
          NavigationDestination(
            icon: Icon(Icons.chat_outlined),
            selectedIcon: Icon(Icons.chat),
            label: 'Xabarlar',
          ),
        ],
      ),
    );
  }
}
