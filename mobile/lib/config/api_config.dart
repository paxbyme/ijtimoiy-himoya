class ApiConfig {
  static const String _envApiBaseUrl = String.fromEnvironment('API_BASE_URL');

  static String get baseUrl {
    if (_envApiBaseUrl.isNotEmpty) return _envApiBaseUrl;
    return 'https://manager-app-production-53c2.up.railway.app/api';
  }

  /// WebSocket base URL derived from baseUrl (https → wss, http → ws).
  static String get wsBaseUrl {
    final url = baseUrl;
    if (url.startsWith('https://')) return 'wss://${url.substring(8)}';
    if (url.startsWith('http://')) return 'ws://${url.substring(7)}';
    return url;
  }
}
