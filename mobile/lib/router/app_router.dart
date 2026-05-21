import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../core/constants/route_names.dart';
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
    initialLocation: Routes.splash,
    redirect: (context, state) {
      final isSplashRoute = state.matchedLocation == Routes.splash;
      final isLoginRoute = state.matchedLocation == Routes.login;

      // Auth state still resolving (Firebase restoring persisted session)
      if (authState.isLoading) {
        return isSplashRoute ? null : Routes.splash;
      }

      final isLoggedIn = authState.value != null;

      if (!isLoggedIn) {
        return isLoginRoute ? null : Routes.login;
      }

      // Logged in — wait for profile to load before routing to dashboard
      if (userProfile.isLoading) {
        return isSplashRoute ? null : Routes.splash;
      }

      final profile = userProfile.value;
      if (profile == null) {
        return isLoginRoute ? null : Routes.login;
      }

      if (isSplashRoute || isLoginRoute) {
        if (profile.role == 'DEVELOPER') return Routes.developerHome;
        return profile.isManager ? Routes.managerHome : Routes.staffHome;
      }

      return null;
    },
    routes: [
      GoRoute(
        path: Routes.splash,
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: Routes.login,
        builder: (context, state) => const LoginScreen(),
      ),

      // Staff routes
      ShellRoute(
        navigatorKey: _staffShellKey,
        builder: (context, state, child) => StaffShell(child: child),
        routes: [
          GoRoute(
            path: Routes.staffHome,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: StaffHomeScreen(),
            ),
          ),
          GoRoute(
            path: Routes.staffTasks,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: MyTasksScreen(),
            ),
          ),
          GoRoute(
            path: Routes.staffAiChat,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: AiChatbotScreen(),
            ),
          ),
          GoRoute(
            path: Routes.staffKpi,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: KpiScreen(),
            ),
          ),
          GoRoute(
            path: Routes.staffChat,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: StaffChatScreen(),
            ),
          ),
          GoRoute(
            path: Routes.staffProfile,
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
            path: Routes.managerHome,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: ManagerHomeScreen(),
            ),
          ),
          GoRoute(
            path: Routes.managerEmployees,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: EmployeeListScreen(),
            ),
            routes: [
              GoRoute(
                path: Routes.managerEmployeeDetailSubPattern,
                builder: (context, state) => EmployeeDetailScreen(
                  employeeId: state.pathParameters['id']!,
                ),
              ),
            ],
          ),
          GoRoute(
            path: Routes.managerTasks,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: TaskManagementScreen(),
            ),
          ),
          GoRoute(
            path: Routes.managerAiRules,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: AiRulesScreen(),
            ),
          ),
          GoRoute(
            path: Routes.managerKpi,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: KpiDashboardScreen(),
            ),
          ),
          GoRoute(
            path: Routes.managerChat,
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
            path: Routes.developerHome,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: DevHomeScreen(),
            ),
          ),
          GoRoute(
            path: Routes.developerManagers,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: DevManagersScreen(),
            ),
          ),
          GoRoute(
            path: Routes.developerDepartments,
            pageBuilder: (context, state) => const NoTransitionPage(
              child: DevDepartmentsScreen(),
            ),
          ),
        ],
      ),

      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: Routes.staffTaskDetailPattern,
        builder: (context, state) => TaskDetailScreen(
          taskId: state.pathParameters['taskId']!,
        ),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: Routes.staffAiChatLive,
        builder: (context, state) => const LiveVoiceScreen(),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: Routes.managerCreateTask,
        builder: (context, state) => const CreateTaskScreen(),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: Routes.managerChatWithStaffPattern,
        builder: (context, state) => ManagerChatScreen(
          staffId: state.pathParameters['staffId']!,
        ),
      ),
    ],
  );
});
