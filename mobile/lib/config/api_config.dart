import '../core/constants/env_config.dart';

/// Thin facade over [EnvConfig] that exposes the API + WebSocket base URLs.
///
/// New code should prefer reading [EnvConfig] directly. This class is kept
/// for callsites that already import `config/api_config.dart`.
class ApiConfig {
  static String get baseUrl => EnvConfig.resolvedApiUrl;

  /// WebSocket base URL derived from baseUrl (https → wss, http → ws).
  static String get wsBaseUrl {
    final url = baseUrl;
    if (url.startsWith('https://')) return 'wss://${url.substring(8)}';
    if (url.startsWith('http://')) return 'ws://${url.substring(7)}';
    return url;
  }
}
