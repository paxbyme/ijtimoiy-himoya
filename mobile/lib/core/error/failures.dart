import 'package:dio/dio.dart';

import 'exceptions.dart';

/// User-facing error type returned by repositories.
///
/// Unlike [Exception]s, a [Failure] is a value that providers and UI
/// can pattern-match on without try/catch. Each variant carries a
/// human-readable [message] that is safe to show to end users.
///
/// Used in Step 2 with `Either<Failure, T>` from the `dartz` package.
sealed class Failure {
  final String message;
  final Object? cause;

  const Failure(this.message, {this.cause});

  @override
  String toString() => '$runtimeType: $message';
}

class ServerFailure extends Failure {
  final int? statusCode;
  const ServerFailure(super.message, {this.statusCode, super.cause});
}

class NetworkFailure extends Failure {
  const NetworkFailure([super.message = 'Internet aloqasi yo\'q']);
}

class AuthFailure extends Failure {
  final String? code;
  const AuthFailure(super.message, {this.code, super.cause});
}

class ValidationFailure extends Failure {
  final Map<String, String>? fieldErrors;
  const ValidationFailure(super.message, {this.fieldErrors, super.cause});
}

class CacheFailure extends Failure {
  const CacheFailure([super.message = 'Cache o\'qib bo\'lmadi']);
}

class UnknownFailure extends Failure {
  const UnknownFailure([super.message = 'Noma\'lum xatolik yuz berdi']);
}

/// Translates raw exceptions (Dio, Firebase, custom) into [Failure] values.
///
/// Used by repositories so providers never need to know about Dio or
/// Firebase types. Backend error responses are expected to be shaped as
/// `ApiResponse<T>` — `{ success, message, data }` — so we extract
/// `message` when present.
class FailureMapper {
  const FailureMapper._();

  static Failure fromException(Object error) {
    if (error is Failure) return error;

    if (error is DioException) return _fromDio(error);

    if (error is NetworkException) return NetworkFailure(error.message);
    if (error is AuthException) {
      return AuthFailure(error.message, code: error.code, cause: error);
    }
    if (error is ValidationException) {
      return ValidationFailure(error.message,
          fieldErrors: error.fieldErrors, cause: error);
    }
    if (error is ServerException) {
      return ServerFailure(error.message,
          statusCode: error.statusCode, cause: error);
    }
    if (error is CacheException) return CacheFailure(error.message);

    return UnknownFailure(error.toString());
  }

  static Failure _fromDio(DioException error) {
    switch (error.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.sendTimeout:
        return const NetworkFailure('So\'rov vaqti tugadi');
      case DioExceptionType.connectionError:
        return const NetworkFailure('Serverga ulanib bo\'lmadi');
      case DioExceptionType.cancel:
        return const UnknownFailure('So\'rov bekor qilindi');
      case DioExceptionType.badCertificate:
        return const NetworkFailure('Server sertifikati noto\'g\'ri');
      case DioExceptionType.badResponse:
      case DioExceptionType.unknown:
        final response = error.response;
        final status = response?.statusCode;
        final message = _extractMessage(response?.data) ??
            'Server xatoligi (${status ?? '-'})';

        if (status == 401 || status == 403) {
          return AuthFailure(message, code: status?.toString(), cause: error);
        }
        if (status == 422 || status == 400) {
          return ValidationFailure(message, cause: error);
        }
        return ServerFailure(message, statusCode: status, cause: error);
    }
  }

  static String? _extractMessage(dynamic body) {
    if (body is Map) {
      final msg = body['message'];
      if (msg is String && msg.isNotEmpty) return msg;
      final error = body['error'];
      if (error is String && error.isNotEmpty) return error;
    }
    return null;
  }
}
