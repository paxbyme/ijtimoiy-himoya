import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_ru.dart';
import 'app_localizations_uz.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppL10n
/// returned by `AppL10n.of(context)`.
///
/// Applications need to include `AppL10n.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppL10n.localizationsDelegates,
///   supportedLocales: AppL10n.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppL10n.supportedLocales
/// property.
abstract class AppL10n {
  AppL10n(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppL10n of(BuildContext context) {
    return Localizations.of<AppL10n>(context, AppL10n)!;
  }

  static const LocalizationsDelegate<AppL10n> delegate = _AppL10nDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('ru'),
    Locale('uz'),
  ];

  /// Application name shown in launchers and headings.
  ///
  /// In uz, this message translates to:
  /// **'Boss Manager'**
  String get appTitle;

  /// Heading on the login form card.
  ///
  /// In uz, this message translates to:
  /// **'Xush kelibsiz'**
  String get loginWelcome;

  /// No description provided for @loginSubtitle.
  ///
  /// In uz, this message translates to:
  /// **'Davom etish uchun ma\'lumotlarni kiriting'**
  String get loginSubtitle;

  /// No description provided for @loginPhoneLabel.
  ///
  /// In uz, this message translates to:
  /// **'Telefon raqami'**
  String get loginPhoneLabel;

  /// No description provided for @loginPhoneHint.
  ///
  /// In uz, this message translates to:
  /// **'Telefon raqamingizni kiriting'**
  String get loginPhoneHint;

  /// No description provided for @loginPasswordLabel.
  ///
  /// In uz, this message translates to:
  /// **'Parol'**
  String get loginPasswordLabel;

  /// No description provided for @loginPasswordHint.
  ///
  /// In uz, this message translates to:
  /// **'Parolingizni kiriting'**
  String get loginPasswordHint;

  /// No description provided for @loginSubmit.
  ///
  /// In uz, this message translates to:
  /// **'Kirish'**
  String get loginSubmit;

  /// No description provided for @loginPhoneRequired.
  ///
  /// In uz, this message translates to:
  /// **'Telefon raqamini kiriting'**
  String get loginPhoneRequired;

  /// No description provided for @loginPasswordRequired.
  ///
  /// In uz, this message translates to:
  /// **'Parolni kiriting'**
  String get loginPasswordRequired;

  /// No description provided for @loginPasswordTooShort.
  ///
  /// In uz, this message translates to:
  /// **'Parol kamida 6 ta belgidan iborat bo\'lishi kerak'**
  String get loginPasswordTooShort;

  /// No description provided for @loginNetworkError.
  ///
  /// In uz, this message translates to:
  /// **'Tarmoq xatosi. Internet aloqasini tekshiring.'**
  String get loginNetworkError;

  /// No description provided for @loginGenericError.
  ///
  /// In uz, this message translates to:
  /// **'Kirish muvaffaqiyatsiz. Qayta urinib ko\'ring.'**
  String get loginGenericError;

  /// No description provided for @navHome.
  ///
  /// In uz, this message translates to:
  /// **'Bosh sahifa'**
  String get navHome;

  /// No description provided for @navTasks.
  ///
  /// In uz, this message translates to:
  /// **'Vazifalar'**
  String get navTasks;

  /// No description provided for @navChat.
  ///
  /// In uz, this message translates to:
  /// **'Suhbat'**
  String get navChat;

  /// No description provided for @navKpi.
  ///
  /// In uz, this message translates to:
  /// **'KPI'**
  String get navKpi;

  /// No description provided for @navProfile.
  ///
  /// In uz, this message translates to:
  /// **'Profil'**
  String get navProfile;

  /// No description provided for @navEmployees.
  ///
  /// In uz, this message translates to:
  /// **'Xodimlar'**
  String get navEmployees;

  /// No description provided for @navAiRules.
  ///
  /// In uz, this message translates to:
  /// **'AI Qoidalari'**
  String get navAiRules;

  /// No description provided for @navAiChat.
  ///
  /// In uz, this message translates to:
  /// **'AI yordamchi'**
  String get navAiChat;

  /// No description provided for @actionLogout.
  ///
  /// In uz, this message translates to:
  /// **'Chiqish'**
  String get actionLogout;

  /// No description provided for @actionCancel.
  ///
  /// In uz, this message translates to:
  /// **'Bekor'**
  String get actionCancel;

  /// No description provided for @actionDelete.
  ///
  /// In uz, this message translates to:
  /// **'O\'chirish'**
  String get actionDelete;

  /// No description provided for @actionSave.
  ///
  /// In uz, this message translates to:
  /// **'Saqlash'**
  String get actionSave;

  /// No description provided for @actionRetry.
  ///
  /// In uz, this message translates to:
  /// **'Qayta urinish'**
  String get actionRetry;

  /// No description provided for @emptyTasks.
  ///
  /// In uz, this message translates to:
  /// **'Hozircha vazifalar yo\'q'**
  String get emptyTasks;

  /// No description provided for @emptyMessages.
  ///
  /// In uz, this message translates to:
  /// **'Hali xabarlar yo\'q. Suhbatni boshlang!'**
  String get emptyMessages;
}

class _AppL10nDelegate extends LocalizationsDelegate<AppL10n> {
  const _AppL10nDelegate();

  @override
  Future<AppL10n> load(Locale locale) {
    return SynchronousFuture<AppL10n>(lookupAppL10n(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['ru', 'uz'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppL10nDelegate old) => false;
}

AppL10n lookupAppL10n(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'ru':
      return AppL10nRu();
    case 'uz':
      return AppL10nUz();
  }

  throw FlutterError(
    'AppL10n.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
