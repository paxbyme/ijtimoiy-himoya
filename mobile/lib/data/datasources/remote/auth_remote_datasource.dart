import 'package:dio/dio.dart';

import '../../../models/auth/user_model.dart';

/// Thin Dio wrapper for auth-related REST calls. No error mapping here —
/// the repository converts Dio errors into [Failure] values.
class AuthRemoteDataSource {
  final Dio _dio;

  AuthRemoteDataSource(this._dio);

  Future<User> getCurrentProfile() async {
    final response = await _dio.get('/users/me');
    return User.fromJson(response.data['data'] ?? response.data);
  }
}
