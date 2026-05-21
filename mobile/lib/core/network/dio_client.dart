import 'package:dio/dio.dart';
import 'package:firebase_auth/firebase_auth.dart' as fb;

import '../constants/app_constants.dart';
import '../constants/env_config.dart';

/// Builds the shared Dio instance used by remote data sources.
///
/// Attaches a Firebase ID token to every request. Each new data source
/// gets the same configured Dio via DI so we don't pay the interceptor
/// cost twice.
class DioClient {
  static Dio create() {
    final dio = Dio(BaseOptions(
      baseUrl: EnvConfig.resolvedApiUrl,
      connectTimeout: AppConstants.connectTimeout,
      receiveTimeout: AppConstants.receiveTimeout,
      sendTimeout: AppConstants.sendTimeout,
      headers: {'Content-Type': 'application/json'},
    ));

    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final user = fb.FirebaseAuth.instance.currentUser;
        if (user != null) {
          final token = await user.getIdToken();
          options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
      onError: (error, handler) {
        if (EnvConfig.enableVerboseLogging || !EnvConfig.isProduction) {
          // ignore: avoid_print
          print('[API ERROR] ${error.requestOptions.method} '
              '${error.requestOptions.path} → ${error.type} '
              '${error.response?.statusCode} body: ${error.response?.data}');
        }
        handler.next(error);
      },
    ));

    return dio;
  }
}
