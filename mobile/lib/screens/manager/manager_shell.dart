import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class ManagerShell extends StatelessWidget {
  final Widget child;

  const ManagerShell({super.key, required this.child});

  int _currentIndex(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;
    if (location.startsWith('/manager/home') ||
        location.startsWith('/manager/employees')) {
      return 0;
    }
    if (location.startsWith('/manager/tasks')) {
      return 1;
    }
    if (location.startsWith('/manager/kpi') ||
        location.startsWith('/manager/ai-rules')) {
      return 2;
    }
    if (location.startsWith('/manager/chat')) {
      return 3;
    }
    return 0;
  }

  void _onTap(BuildContext context, int index) {
    switch (index) {
      case 0:
        context.go('/manager/employees');
        break;
      case 1:
        context.go('/manager/tasks');
        break;
      case 2:
        context.go('/manager/kpi');
        break;
      case 3:
        context.go('/manager/chat');
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: child,
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex(context),
        onTap: (index) => _onTap(context, index),
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.people_outlined),
            activeIcon: Icon(Icons.people),
            label: 'Employees',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.task_alt_outlined),
            activeIcon: Icon(Icons.task_alt),
            label: 'Tasks',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.bar_chart_outlined),
            activeIcon: Icon(Icons.bar_chart),
            label: 'KPIs',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.chat_outlined),
            activeIcon: Icon(Icons.chat),
            label: 'Chat',
          ),
        ],
      ),
    );
  }
}
