import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class ManagerShell extends StatelessWidget {
  final Widget child;

  const ManagerShell({super.key, required this.child});

  int _currentIndex(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;
    if (location.startsWith('/manager/home')) return 0;
    if (location.startsWith('/manager/employees')) return 1;
    if (location.startsWith('/manager/tasks')) return 2;
    if (location.startsWith('/manager/kpi') ||
        location.startsWith('/manager/ai-rules')) {
      return 3;
    }
    if (location.startsWith('/manager/chat')) return 4;
    return 0;
  }

  void _onTap(BuildContext context, int index) {
    switch (index) {
      case 0:
        context.go('/manager/home');
        break;
      case 1:
        context.go('/manager/employees');
        break;
      case 2:
        context.go('/manager/tasks');
        break;
      case 3:
        context.go('/manager/kpi');
        break;
      case 4:
        context.go('/manager/chat');
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
                  opacity: 0.04,
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
