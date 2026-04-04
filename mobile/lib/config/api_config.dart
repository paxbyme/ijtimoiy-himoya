import 'dart:io';

class ApiConfig {
  static const String _envApiBaseUrl = String.fromEnvironment('API_BASE_URL');

  static String get baseUrl {
    if (_envApiBaseUrl.isNotEmpty) return _envApiBaseUrl;
    if (Platform.isAndroid) return 'http://127.0.0.1:8080/api';
    return 'http://localhost:8080/api';
  }
}
