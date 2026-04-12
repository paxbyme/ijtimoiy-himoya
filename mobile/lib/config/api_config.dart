class ApiConfig {
  static const String _envApiBaseUrl = String.fromEnvironment('API_BASE_URL');

  static String get baseUrl {
    if (_envApiBaseUrl.isNotEmpty) return _envApiBaseUrl;
    return 'https://manager-app-production-53c2.up.railway.app/api';
  }
}
