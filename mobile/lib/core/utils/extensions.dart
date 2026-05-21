import 'package:flutter/material.dart';

import '../constants/app_constants.dart';
import 'formatters.dart';
import 'validators.dart';

extension StringX on String {
  String capitalize() {
    if (isEmpty) return this;
    return '${this[0].toUpperCase()}${substring(1)}';
  }

  String capitalizeWords() => split(' ').map((w) => w.capitalize()).join(' ');

  bool get isValidUzPhone => Validators.normalizeUzPhone(this) != null;

  /// Returns `998901234567` form, or `this` unchanged if not parseable.
  String get normalizedUzPhone =>
      Validators.normalizeUzPhone(this) ?? this;

  String truncate(int max, {String suffix = '…'}) {
    if (length <= max) return this;
    return '${substring(0, max)}$suffix';
  }
}

extension DateTimeX on DateTime {
  String get toUzbekDate => Formatters.date(this);
  String get toUzbekDateTime => Formatters.dateTime(this);
  String get toShortDate => Formatters.shortDate(this);
  String get timeAgo => Formatters.timeAgo(this);
  String get asDeadline => Formatters.deadline(this);

  bool isSameDay(DateTime other) =>
      year == other.year && month == other.month && day == other.day;

  bool get isToday => isSameDay(DateTime.now());
  bool get isPast => isBefore(DateTime.now());
}

extension BuildContextX on BuildContext {
  ThemeData get theme => Theme.of(this);
  ColorScheme get colors => Theme.of(this).colorScheme;
  TextTheme get textTheme => Theme.of(this).textTheme;
  MediaQueryData get mq => MediaQuery.of(this);
  Size get screenSize => MediaQuery.sizeOf(this);

  void showSnackBar(
    String message, {
    Color? backgroundColor,
    Duration duration = AppConstants.snackBarDuration,
    SnackBarAction? action,
  }) {
    ScaffoldMessenger.of(this)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(
        content: Text(message),
        backgroundColor: backgroundColor,
        duration: duration,
        action: action,
        behavior: SnackBarBehavior.floating,
      ));
  }

  void showErrorSnackBar(String message) =>
      showSnackBar(message, backgroundColor: colors.error);

  void showSuccessSnackBar(String message) =>
      showSnackBar(message, backgroundColor: Colors.green.shade700);

  void dismissKeyboard() => FocusScope.of(this).unfocus();
}
