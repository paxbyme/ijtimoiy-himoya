import '../constants/app_constants.dart';

/// Form field validators used across login, staff creation, task forms, etc.
///
/// Each validator returns `null` when the input is valid, or a localized
/// error string when invalid. The signature matches Flutter's
/// `FormFieldValidator<String>` so they can be passed directly to
/// `TextFormField.validator`.
abstract class Validators {
  /// Matches Uzbek mobile numbers in any of these forms:
  /// - `+998901234567`
  /// - `998901234567`
  /// - `901234567`
  /// - `+998 90 123 45 67` (with spaces/dashes)
  static final RegExp _uzPhone =
      RegExp(r'^(?:\+?998)?(\d{2})(\d{3})(\d{2})(\d{2})$');

  static final RegExp _email = RegExp(
      r"^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");

  /// Returns the normalized 12-digit form (`998XXXXXXXXX`) or `null` if invalid.
  static String? normalizeUzPhone(String? input) {
    if (input == null) return null;
    final cleaned = input.replaceAll(RegExp(r'[\s\-()]'), '');
    final match = _uzPhone.firstMatch(cleaned);
    if (match == null) return null;
    return '998${match.group(1)}${match.group(2)}${match.group(3)}${match.group(4)}';
  }

  static String? uzPhone(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Telefon raqami kiritilmagan';
    }
    if (normalizeUzPhone(value) == null) {
      return 'Noto\'g\'ri telefon format (+998 XX XXX XX XX)';
    }
    return null;
  }

  static String? required(String? value, {String fieldName = 'Maydon'}) {
    if (value == null || value.trim().isEmpty) {
      return '$fieldName to\'ldirilmagan';
    }
    return null;
  }

  static String? password(String? value) {
    if (value == null || value.isEmpty) {
      return 'Parol kiritilmagan';
    }
    if (value.length < AppConstants.minPasswordLength) {
      return 'Parol kamida ${AppConstants.minPasswordLength} ta belgidan iborat bo\'lishi kerak';
    }
    if (value.length > AppConstants.maxPasswordLength) {
      return 'Parol ${AppConstants.maxPasswordLength} ta belgidan oshmasligi kerak';
    }
    return null;
  }

  static String? email(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Email kiritilmagan';
    }
    if (!_email.hasMatch(value.trim())) {
      return 'Noto\'g\'ri email format';
    }
    return null;
  }

  static String? name(String? value) {
    final base = required(value, fieldName: 'Ism');
    if (base != null) return base;
    if (value!.trim().length > AppConstants.maxNameLength) {
      return 'Ism ${AppConstants.maxNameLength} ta belgidan oshmasligi kerak';
    }
    return null;
  }

  static String? maxLength(String? value, int max, {String fieldName = 'Maydon'}) {
    if (value == null) return null;
    if (value.length > max) {
      return '$fieldName $max ta belgidan oshmasligi kerak';
    }
    return null;
  }

  /// Composes multiple validators — returns the first non-null error.
  static String? Function(String?) compose(
      List<String? Function(String?)> validators) {
    return (value) {
      for (final v in validators) {
        final result = v(value);
        if (result != null) return result;
      }
      return null;
    };
  }
}
