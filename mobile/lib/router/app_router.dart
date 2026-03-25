import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/auth_provider.dart';
import '../screens/auth/login_screen.dart';
import '../screens/staff/staff_shell.dart';
import '../screens/staff/ai_chatbot_screen.dart';
import '../screens/staff/my_tasks_screen.dart';
import '../screens/staff/task_detail_screen.dart';
import '../screens/staff/kpi_screen.dart';
import '../screens/staff/staff_chat_screen.dart';
import '../screens/manager/manager_shell.dart';
import '../screens/manager/manager_home_screen.dart';
import '../screens/manager/employee_list_screen.dart';
import '../screens/manager/employee_detail_screen.dart';
import '../screens/manager/task_management_screen.dart';
import '../screens/manager/create_task_screen.dart';
import '../screens/manager/ai_rules_screen.dart';
import '../screens/manager/kpi_dashboard_screen.dart';
import '../screens/manager/manager_chat_list_screen.dart';
import '../screens/manager/manager_chat_screen.dart';

final _rootNavigatorKey = GlobalKey<NavigatorState>();
final _staffShellKey = GlobalKey<NavigatorState>();
final _managerShellKey = GlobalKey<NavigatorState>();

final routerProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authStateProvider);
  final userProfile = ref.watch(userProfileProvider);

  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: '/login',
    redirect: (context, state) {
      final isLoggedIn = authState.value != null;
      final isLoginRoute = state.matchedLocation == '/login';

      if (!isLoggedIn && !isLoginRoute) {
        return '/login';
      }

      if (isLoggedIn && isLoginRoute) {
        final profile = userProfile.value;
        if (profile != null) {
          return profile.isManager ? '/manager/home' : '/staff/ai-chat';
        }
        // Profile still loading, stay on login for now
        return null;
      }

      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),

      // Staff routes
      ShellRoute(
        navigatorKey: _staffShellKey,
        builder: (context, state, child) => StaffShell(child: child),
        routes: [
          GoRoute(
            path: '/staff/ai-chat',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: AiChatbotScreen(),
            ),
          ),
          GoRoute(
            path: '/staff/tasks',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: MyTasksScreen(),
            ),
          ),
          GoRoute(
            path: '/staff/kpi',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: KpiScreen(),
            ),
          ),
          GoRoute(
            path: '/staff/chat',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: StaffChatScreen(),
            ),
          ),
        ],
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/staff/tasks/:id',
        builder: (context, state) => TaskDetailScreen(
          taskId: state.pathParameters['id']!,
        ),
      ),

      // Manager routes
      ShellRoute(
        navigatorKey: _managerShellKey,
        builder: (context, state, child) => ManagerShell(child: child),
        routes: [
          GoRoute(
            path: '/manager/home',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: ManagerHomeScreen(),
            ),
          ),
          GoRoute(
            path: '/manager/employees',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: EmployeeListScreen(),
            ),
          ),
          GoRoute(
            path: '/manager/tasks',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: TaskManagementScreen(),
            ),
          ),
          GoRoute(
            path: '/manager/ai-rules',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: AiRulesScreen(),
            ),
          ),
          GoRoute(
            path: '/manager/kpi',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: KpiDashboardScreen(),
            ),
          ),
          GoRoute(
            path: '/manager/chat',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: ManagerChatListScreen(),
            ),
          ),
        ],
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/manager/employees/:id',
        builder: (context, state) => EmployeeDetailScreen(
          employeeId: state.pathParameters['id']!,
        ),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/manager/tasks/create',
        builder: (context, state) => const CreateTaskScreen(),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/manager/chat/:staffId',
        builder: (context, state) => ManagerChatScreen(
          staffId: state.pathParameters['staffId']!,
        ),
      ),
    ],
  );
});
