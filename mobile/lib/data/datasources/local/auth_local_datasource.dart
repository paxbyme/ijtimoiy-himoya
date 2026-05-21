import 'package:firebase_auth/firebase_auth.dart' as fb;

import '../../../core/error/exceptions.dart';

/// Wraps FirebaseAuth — the only place in the data layer that touches it.
///
/// Throws low-level [AuthException]s. The repository maps these to
/// [Failure] values.
class AuthLocalDataSource {
  final fb.FirebaseAuth _auth;

  AuthLocalDataSource([fb.FirebaseAuth? auth])
      : _auth = auth ?? fb.FirebaseAuth.instance;

  fb.User? get currentUser => _auth.currentUser;

  Stream<fb.User?> get authStateChanges => _auth.authStateChanges();

  /// Phone is mapped to email `{phone}@manager.local` (backend convention).
  Future<fb.UserCredential> signInWithPhone(String phone, String password) async {
    try {
      return await _auth.signInWithEmailAndPassword(
        email: '$phone@manager.local',
        password: password,
      );
    } on fb.FirebaseAuthException catch (e) {
      throw AuthException(_friendlyMessage(e), code: e.code);
    }
  }

  Future<void> signOut() => _auth.signOut();

  Future<String?> getIdToken({bool forceRefresh = false}) async =>
      _auth.currentUser?.getIdToken(forceRefresh);

  String _friendlyMessage(fb.FirebaseAuthException e) {
    switch (e.code) {
      case 'user-not-found':
      case 'invalid-credential':
      case 'wrong-password':
        return 'Telefon yoki parol noto\'g\'ri';
      case 'too-many-requests':
        return 'Juda ko\'p urinish — keyinroq qayta urinib ko\'ring';
      case 'network-request-failed':
        return 'Internet aloqasi yo\'q';
      case 'user-disabled':
        return 'Hisob bloklangan';
      default:
        return e.message ?? 'Kirishda xatolik (${e.code})';
    }
  }
}
