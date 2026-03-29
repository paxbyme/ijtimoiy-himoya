import 'dart:io';

class ApiConfig {
  static const String _envApiBaseUrl = String.fromEnvironment('API_BASE_URL');

  static String get baseUrl {
    if (_envApiBaseUrl.isNotEmpty) return _envApiBaseUrl;
    if (Platform.isAndroid) return 'http://10.0.2.2:8080/api';
    return 'http://localhost:8080/api';
  }
}
