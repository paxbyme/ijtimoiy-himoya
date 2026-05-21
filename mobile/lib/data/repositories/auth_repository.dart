import 'package:dartz/dartz.dart';
import 'package:firebase_auth/firebase_auth.dart' as fb;

import '../../core/error/failures.dart';
import '../../core/network/network_info.dart';
import '../../models/auth/user_model.dart';
import '../datasources/local/auth_local_datasource.dart';
import '../datasources/remote/auth_remote_datasource.dart';

/// Coordinates Firebase Auth (local) and the backend `/users/me` call (remote).
///
/// All public methods return `Either<Failure, T>` so the UI never has to
/// reason about Dio or FirebaseAuth exceptions directly.
class AuthRepository {
  final AuthLocalDataSource _local;
  final AuthRemoteDataSource _remote;
  final NetworkInfo _network;

  AuthRepository(this._local, this._remote, this._network);

  // ---- Passthroughs (no failure mapping needed) ----

  Stream<fb.User?> get authStateChanges => _local.authStateChanges;

  fb.User? get currentFirebaseUser => _local.currentUser;

  Future<String?> getIdToken({bool forceRefresh = false}) =>
      _local.getIdToken(forceRefresh: forceRefresh);

  // ---- Failure-returning operations ----

  Future<Either<Failure, fb.UserCredential>> signIn(
      String phone, String password) async {
    if (!await _network.isConnected) return const Left(NetworkFailure());
    try {
      final cred = await _local.signInWithPhone(phone, password);
      return Right(cred);
    } catch (e) {
      return Left(FailureMapper.fromException(e));
    }
  }

  Future<Either<Failure, void>> signOut() async {
    try {
      await _local.signOut();
      return const Right(null);
    } catch (e) {
      return Left(FailureMapper.fromException(e));
    }
  }

  /// Loads the backend user profile. Returns `Right(null)` when there is
  /// no signed-in Firebase user (callers can treat that as "logged out").
  Future<Either<Failure, User?>> getCurrentProfile() async {
    if (_local.currentUser == null) return const Right(null);
    if (!await _network.isConnected) return const Left(NetworkFailure());
    try {
      final user = await _remote.getCurrentProfile();
      return Right(user);
    } catch (e) {
      return Left(FailureMapper.fromException(e));
    }
  }
}
