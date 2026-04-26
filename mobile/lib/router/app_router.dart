import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/auth_provider.dart';
import '../screens/auth/login_screen.dart';
import '../screens/auth/splash_screen.dart';
import '../screens/staff/staff_shell.dart';
import '../screens/staff/staff_home_screen.dart';
import '../screens/staff/staff_profile_screen.dart';
import '../screens/staff/kpi_screen.dart';
import '../screens/staff/ai_chatbot_screen.dart';
import '../screens/staff/live_voice_screen.dart';
import '../screens/staff/my_tasks_screen.dart';
import '../screens/staff/staff_chat_screen.dart';
import '../screens/staff/task_detail_screen.dart';
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
import '../screens/developer/developer_shell.dart';
import '../screens/developer/dev_home_screen.dart';
import '../screens/developer/dev_managers_screen.dart';
import '../screens/developer/dev_departments_screen.dart';

final _rootNavigatorKey = GlobalKey<NavigatorState>();
final _staffShellKey = GlobalKey<NavigatorState>();
final _managerShellKey = GlobalKey<NavigatorState>();
final _developerShellKey = GlobalKey<NavigatorState>();

final routerProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authStateProvider);
  final userProfile = ref.watch(userProfileProvider);

  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: '/',
    redirect: (context, state) {
      final isSplashRoute = state.matchedLocation == '/';
      final isLoginRoute = state.matchedLocation == '/login';

      // Auth state still resolving (Firebase restoring persisted session)
      if (authState.isLoading) {
        return isSplashRoute ? null : '/';
      }

      final isLoggedIn = authState.value != null;

      if (!isLoggedIn) {
        return isLoginRoute ? null : '/login';
      }

      // Logged in — wait for profile to load before routing to dashboard
      if (userProfile.isLoading) {
        return isSplashRoute ? null : '/';
      }

      final profile = userProfile.value;
      if (profile == null) {
        return isLoginRoute ? null : '/login';
      }

      if (isSplashRoute || isLoginRoute) {
        if (profile.role == 'DEVELOPER') return '/developer/home';
        return profile.isManager ? '/manager/home' : '/staff/home';
      }

      return null;
    },
    routes: [
      GoRoute(
        path: '/',
        builder: (context, state) => const SplashScreen(),
      ),
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
            path: '/staff/home',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: StaffHomeScreen(),
            ),
          ),
          GoRoute(
            path: '/staff/tasks',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: MyTasksScreen(),
            ),
          ),
          GoRoute(
            path: '/staff/ai-chat',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: AiChatbotScreen(),
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
          GoRoute(
            path: '/staff/profile',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: StaffProfileScreen(),
            ),
          ),
        ],
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
            routes: [
              GoRoute(
                path: ':id',
                builder: (context, state) => EmployeeDetailScreen(
                  employeeId: state.pathParameters['id']!,
                ),
              ),
            ],
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
      // Developer routes
      ShellRoute(
        navigatorKey: _developerShellKey,
        builder: (context, state, child) => DeveloperShell(child: child),
        routes: [
          GoRoute(
            path: '/developer/home',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: DevHomeScreen(),
            ),
          ),
          GoRoute(
            path: '/developer/managers',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: DevManagersScreen(),
            ),
          ),
          GoRoute(
            path: '/developer/departments',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: DevDepartmentsScreen(),
            ),
          ),
        ],
      ),

      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/staff/tasks/:taskId',
        builder: (context, state) => TaskDetailScreen(
          taskId: state.pathParameters['taskId']!,
        ),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/staff/ai-chat/live',
        builder: (context, state) => const LiveVoiceScreen(),
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
