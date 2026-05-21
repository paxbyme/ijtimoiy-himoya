/// Application-wide constants.
///
/// Centralizes magic numbers and configuration values that are used
/// across multiple layers of the app. Keep this file small — anything
/// that varies by environment belongs in [EnvConfig] instead.
abstract class AppConstants {
  // Network
  static const Duration connectTimeout = Duration(seconds: 60);
  static const Duration receiveTimeout = Duration(seconds: 60);
  static const Duration sendTimeout = Duration(seconds: 60);
  static const Duration uploadTimeout = Duration(seconds: 120);
  static const Duration sseTimeout = Duration(seconds: 120);

  // Pagination
  static const int defaultPageSize = 20;
  static const int maxPageSize = 100;

  // Validation limits
  static const int minPasswordLength = 6;
  static const int maxPasswordLength = 64;
  static const int maxNameLength = 100;
  static const int maxTaskTitleLength = 200;
  static const int maxTaskDescriptionLength = 2000;
  static const int maxChatMessageLength = 4000;

  // UI
  static const Duration snackBarDuration = Duration(seconds: 3);
  static const Duration shortAnimation = Duration(milliseconds: 200);
  static const Duration mediumAnimation = Duration(milliseconds: 400);

  // Locale defaults
  static const String defaultLocale = 'uz';
  static const String fallbackLocale = 'ru';

  // Storage keys (for future shared_preferences / secure storage)
  static const String prefsLastUserId = 'last_user_id';
  static const String prefsLocale = 'app_locale';
}
