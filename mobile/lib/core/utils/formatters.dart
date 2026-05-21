import 'package:intl/intl.dart';

/// Display formatters for dates, numbers, and currency.
///
/// Locale-aware — defaults assume Uzbek conventions. For multi-locale
/// support after Step 3.1, pass an explicit locale.
abstract class Formatters {
  static final DateFormat _date = DateFormat('dd MMM, yyyy');
  static final DateFormat _dateTime = DateFormat('dd MMM, yyyy HH:mm');
  static final DateFormat _time = DateFormat('HH:mm');
  static final DateFormat _shortDate = DateFormat('dd.MM.yyyy');
  static final NumberFormat _decimal = NumberFormat.decimalPattern('uz');

  static String date(DateTime value) => _date.format(value);
  static String dateTime(DateTime value) => _dateTime.format(value);
  static String time(DateTime value) => _time.format(value);
  static String shortDate(DateTime value) => _shortDate.format(value);

  static String number(num value) => _decimal.format(value);

  static String currency(num value, {String symbol = "so'm"}) {
    return '${_decimal.format(value)} $symbol';
  }

  /// Renders a human-readable relative time in Uzbek.
  ///
  /// Examples: `hozir`, `5 daqiqa oldin`, `2 soat oldin`, `Kecha`,
  /// `3 kun oldin`. Falls back to absolute date for older timestamps.
  static String timeAgo(DateTime value, {DateTime? now}) {
    final reference = now ?? DateTime.now();
    final diff = reference.difference(value);

    if (diff.isNegative) return date(value);
    if (diff.inSeconds < 30) return 'hozir';
    if (diff.inMinutes < 1) return '${diff.inSeconds} soniya oldin';
    if (diff.inMinutes < 60) return '${diff.inMinutes} daqiqa oldin';
    if (diff.inHours < 24) return '${diff.inHours} soat oldin';
    if (diff.inDays == 1) return 'Kecha';
    if (diff.inDays < 7) return '${diff.inDays} kun oldin';
    if (diff.inDays < 30) return '${(diff.inDays / 7).floor()} hafta oldin';
    return date(value);
  }

  /// Renders a deadline-style label: `Bugun 15:00`, `Ertaga 09:00`,
  /// `Kecha 18:00`, otherwise full date.
  static String deadline(DateTime value, {DateTime? now}) {
    final reference = now ?? DateTime.now();
    final today = DateTime(reference.year, reference.month, reference.day);
    final target = DateTime(value.year, value.month, value.day);
    final diff = target.difference(today).inDays;

    if (diff == 0) return 'Bugun ${time(value)}';
    if (diff == 1) return 'Ertaga ${time(value)}';
    if (diff == -1) return 'Kecha ${time(value)}';
    return dateTime(value);
  }

  /// Formats phone number from `998901234567` to `+998 90 123 45 67`.
  static String phone(String raw) {
    final digits = raw.replaceAll(RegExp(r'\D'), '');
    if (digits.length != 12 || !digits.startsWith('998')) return raw;
    final cc = digits.substring(0, 3);
    final op = digits.substring(3, 5);
    final p1 = digits.substring(5, 8);
    final p2 = digits.substring(8, 10);
    final p3 = digits.substring(10, 12);
    return '+$cc $op $p1 $p2 $p3';
  }
}
