// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Uzbek (`uz`).
class AppL10nUz extends AppL10n {
  AppL10nUz([String locale = 'uz']) : super(locale);

  @override
  String get appTitle => 'Boss Manager';

  @override
  String get loginWelcome => 'Xush kelibsiz';

  @override
  String get loginSubtitle => 'Davom etish uchun ma\'lumotlarni kiriting';

  @override
  String get loginPhoneLabel => 'Telefon raqami';

  @override
  String get loginPhoneHint => 'Telefon raqamingizni kiriting';

  @override
  String get loginPasswordLabel => 'Parol';

  @override
  String get loginPasswordHint => 'Parolingizni kiriting';

  @override
  String get loginSubmit => 'Kirish';

  @override
  String get loginPhoneRequired => 'Telefon raqamini kiriting';

  @override
  String get loginPasswordRequired => 'Parolni kiriting';

  @override
  String get loginPasswordTooShort =>
      'Parol kamida 6 ta belgidan iborat bo\'lishi kerak';

  @override
  String get loginNetworkError =>
      'Tarmoq xatosi. Internet aloqasini tekshiring.';

  @override
  String get loginGenericError =>
      'Kirish muvaffaqiyatsiz. Qayta urinib ko\'ring.';

  @override
  String get navHome => 'Bosh sahifa';

  @override
  String get navTasks => 'Vazifalar';

  @override
  String get navChat => 'Suhbat';

  @override
  String get navKpi => 'KPI';

  @override
  String get navProfile => 'Profil';

  @override
  String get navEmployees => 'Xodimlar';

  @override
  String get navAiRules => 'AI Qoidalari';

  @override
  String get navAiChat => 'AI yordamchi';

  @override
  String get actionLogout => 'Chiqish';

  @override
  String get actionCancel => 'Bekor';

  @override
  String get actionDelete => 'O\'chirish';

  @override
  String get actionSave => 'Saqlash';

  @override
  String get actionRetry => 'Qayta urinish';

  @override
  String get emptyTasks => 'Hozircha vazifalar yo\'q';

  @override
  String get emptyMessages => 'Hali xabarlar yo\'q. Suhbatni boshlang!';
}
