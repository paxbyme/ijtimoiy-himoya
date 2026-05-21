/// Low-level exceptions thrown by data sources.
///
/// Data sources (`*_remote_datasource.dart`, `*_local_datasource.dart`) throw
/// these. Repositories catch them and translate to `Failure` values that
/// flow up to providers via `Either<Failure, T>` (introduced in Step 2).
///
/// Keep these dumb — they carry context, not behavior.
class ServerException implements Exception {
  final String message;
  final int? statusCode;
  final dynamic body;

  const ServerException(this.message, {this.statusCode, this.body});

  @override
  String toString() =>
      'ServerException(${statusCode ?? '-'}): $message';
}

class NetworkException implements Exception {
  final String message;
  const NetworkException([this.message = 'Tarmoqqa ulanish yo\'q']);

  @override
  String toString() => 'NetworkException: $message';
}

class AuthException implements Exception {
  final String message;
  final String? code;

  const AuthException(this.message, {this.code});

  @override
  String toString() => 'AuthException${code != null ? '($code)' : ''}: $message';
}

class CacheException implements Exception {
  final String message;
  const CacheException([this.message = 'Cache xatoligi']);

  @override
  String toString() => 'CacheException: $message';
}

class ValidationException implements Exception {
  final String message;
  final Map<String, String>? fieldErrors;

  const ValidationException(this.message, {this.fieldErrors});

  @override
  String toString() => 'ValidationException: $message';
}
