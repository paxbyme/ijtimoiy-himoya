import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class StaffShell extends StatelessWidget {
  final Widget child;

  const StaffShell({super.key, required this.child});

  int _currentIndex(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;
    if (location.startsWith('/staff/ai-chat')) return 0;
    if (location.startsWith('/staff/tasks')) return 1;
    if (location.startsWith('/staff/kpi')) return 2;
    if (location.startsWith('/staff/chat')) return 3;
    return 0;
  }

  void _onTap(BuildContext context, int index) {
    switch (index) {
      case 0:
        context.go('/staff/ai-chat');
        break;
      case 1:
        context.go('/staff/tasks');
        break;
      case 2:
        context.go('/staff/kpi');
        break;
      case 3:
        context.go('/staff/chat');
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
            icon: Icon(Icons.smart_toy_outlined),
            activeIcon: Icon(Icons.smart_toy),
            label: 'AI Chat',
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
