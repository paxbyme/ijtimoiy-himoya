// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Russian (`ru`).
class AppL10nRu extends AppL10n {
  AppL10nRu([String locale = 'ru']) : super(locale);

  @override
  String get appTitle => 'Boss Manager';

  @override
  String get loginWelcome => 'Добро пожаловать';

  @override
  String get loginSubtitle => 'Введите данные для входа';

  @override
  String get loginPhoneLabel => 'Номер телефона';

  @override
  String get loginPhoneHint => 'Введите номер телефона';

  @override
  String get loginPasswordLabel => 'Пароль';

  @override
  String get loginPasswordHint => 'Введите пароль';

  @override
  String get loginSubmit => 'Войти';

  @override
  String get loginPhoneRequired => 'Введите номер телефона';

  @override
  String get loginPasswordRequired => 'Введите пароль';

  @override
  String get loginPasswordTooShort => 'Пароль должен быть не короче 6 символов';

  @override
  String get loginNetworkError => 'Ошибка сети. Проверьте подключение.';

  @override
  String get loginGenericError => 'Не удалось войти. Попробуйте ещё раз.';

  @override
  String get navHome => 'Главная';

  @override
  String get navTasks => 'Задачи';

  @override
  String get navChat => 'Чат';

  @override
  String get navKpi => 'KPI';

  @override
  String get navProfile => 'Профиль';

  @override
  String get navEmployees => 'Сотрудники';

  @override
  String get navAiRules => 'Правила ИИ';

  @override
  String get navAiChat => 'ИИ-помощник';

  @override
  String get actionLogout => 'Выйти';

  @override
  String get actionCancel => 'Отмена';

  @override
  String get actionDelete => 'Удалить';

  @override
  String get actionSave => 'Сохранить';

  @override
  String get actionRetry => 'Повторить';

  @override
  String get emptyTasks => 'Пока нет задач';

  @override
  String get emptyMessages => 'Сообщений пока нет. Начните разговор!';
}
