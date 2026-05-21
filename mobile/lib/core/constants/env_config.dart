/// Environment-driven configuration.
///
/// Values come from `--dart-define` flags at build time. Defaults target
/// the dev environment so `flutter run` works without flags.
///
/// Build commands (see README):
///   flutter run --dart-define=ENV=dev
///   flutter build apk --dart-define=ENV=staging --dart-define=API_URL=...
///   flutter build apk --release --dart-define=ENV=prod --dart-define=API_URL=...
abstract class EnvConfig {
  /// `dev` | `staging` | `prod`.
  static const String env = String.fromEnvironment('ENV', defaultValue: 'dev');

  /// REST API base URL (must include `/api` suffix).
  ///
  /// When empty, [resolvedApiUrl] falls back to the per-environment default.
  static const String _apiUrlOverride =
      String.fromEnvironment('API_URL', defaultValue: '');

  /// Legacy override — kept for backwards compatibility with existing
  /// `--dart-define=API_BASE_URL=...` invocations.
  static const String _legacyApiBaseUrl =
      String.fromEnvironment('API_BASE_URL', defaultValue: '');

  /// Production-only Sentry/analytics flags can be added here later.
  static const bool enableVerboseLogging =
      bool.fromEnvironment('VERBOSE_LOGS', defaultValue: false);

  static bool get isProduction => env == 'prod';
  static bool get isStaging => env == 'staging';
  static bool get isDevelopment => env == 'dev';

  /// Resolves the API URL with this precedence:
  ///   1. `--dart-define=API_URL=...`
  ///   2. legacy `--dart-define=API_BASE_URL=...`
  ///   3. per-environment default
  static String get resolvedApiUrl {
    if (_apiUrlOverride.isNotEmpty) return _apiUrlOverride;
    if (_legacyApiBaseUrl.isNotEmpty) return _legacyApiBaseUrl;
    return _defaultApiUrlFor(env);
  }

  static String _defaultApiUrlFor(String env) {
    switch (env) {
      case 'prod':
        return 'https://manager-app-production-53c2.up.railway.app/api';
      case 'staging':
        return 'https://manager-app-production-53c2.up.railway.app/api';
      case 'dev':
      default:
        return 'https://manager-app-production-53c2.up.railway.app/api';
    }
  }
}
