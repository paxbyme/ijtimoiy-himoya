/// Centralized route paths for GoRouter.
///
/// All route strings live here as compile-time constants. This prevents
/// typos in `context.go(...)` calls and makes refactoring routes trivial.
///
/// Parameterized routes are exposed as static helper methods that return
/// the fully-resolved path (e.g. [Routes.taskDetail] for `/staff/tasks/:taskId`).
///
/// NOTE: This file will be wired into `app_router.dart` and screen-level
/// navigation calls during Step 1.2. For now it acts as the source of truth.
abstract class Routes {
  // --- Auth / Entry ---
  static const String splash = '/';
  static const String login = '/login';

  // --- Staff shell + tabs ---
  static const String staffHome = '/staff/home';
  static const String staffTasks = '/staff/tasks';
  static const String staffAiChat = '/staff/ai-chat';
  static const String staffKpi = '/staff/kpi';
  static const String staffChat = '/staff/chat';
  static const String staffProfile = '/staff/profile';

  // --- Staff detail routes (root navigator) ---
  static const String staffAiChatLive = '/staff/ai-chat/live';

  /// Path template — used by `GoRoute(path:)`. Includes the `:taskId` placeholder.
  static const String staffTaskDetailPattern = '/staff/tasks/:taskId';

  /// `/staff/tasks/:taskId`
  static String staffTaskDetail(String taskId) => '/staff/tasks/$taskId';

  // --- Manager shell + tabs ---
  static const String managerHome = '/manager/home';
  static const String managerEmployees = '/manager/employees';
  static const String managerTasks = '/manager/tasks';
  static const String managerAiRules = '/manager/ai-rules';
  static const String managerKpi = '/manager/kpi';
  static const String managerChat = '/manager/chat';

  // --- Manager detail routes ---
  static const String managerCreateTask = '/manager/tasks/create';

  /// Sub-route path under [managerEmployees]. Use [managerEmployeeDetail] to navigate.
  static const String managerEmployeeDetailSubPattern = ':id';

  /// `/manager/employees/:id`
  static String managerEmployeeDetail(String employeeId) =>
      '/manager/employees/$employeeId';

  /// Path template — used by `GoRoute(path:)`. Includes the `:staffId` placeholder.
  static const String managerChatWithStaffPattern = '/manager/chat/:staffId';

  /// `/manager/chat/:staffId`
  static String managerChatWithStaff(String staffId) =>
      '/manager/chat/$staffId';

  // --- Developer shell + tabs ---
  static const String developerHome = '/developer/home';
  static const String developerManagers = '/developer/managers';
  static const String developerDepartments = '/developer/departments';
}
