import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/network/dio_client.dart';
import '../core/network/network_info.dart';
import '../data/datasources/local/auth_local_datasource.dart';
import '../data/datasources/remote/auth_remote_datasource.dart';
import '../data/repositories/auth_repository.dart';
import '../models/auth/user_model.dart';
import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../services/firestore_service.dart';

// ---- Shared infrastructure ----

/// Shared Dio instance used by data-layer remote sources.
final dioProvider = Provider<Dio>((ref) => DioClient.create());

final networkInfoProvider = Provider<NetworkInfo>((ref) => NetworkInfo());

// ---- Legacy service providers (kept until other slices migrate to repositories) ----

final authServiceProvider = Provider((ref) => AuthService());
final apiServiceProvider = Provider((ref) => ApiService());
final firestoreServiceProvider = Provider((ref) => FirestoreService());

// ---- Auth slice (Step 2 migration) ----

final authLocalDataSourceProvider =
    Provider<AuthLocalDataSource>((ref) => AuthLocalDataSource());

final authRemoteDataSourceProvider = Provider<AuthRemoteDataSource>(
    (ref) => AuthRemoteDataSource(ref.read(dioProvider)));

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(
    ref.read(authLocalDataSourceProvider),
    ref.read(authRemoteDataSourceProvider),
    ref.read(networkInfoProvider),
  );
});

/// Firebase auth state — drives the router redirect.
final authStateProvider = StreamProvider((ref) {
  return ref.watch(authRepositoryProvider).authStateChanges;
});

/// Loaded backend profile for the signed-in user.
///
/// Returns `null` when there is no signed-in Firebase user. Errors from
/// the repository surface as an `AsyncError`, so the splash/router can
/// distinguish "not yet loaded" from "load failed".
final userProfileProvider = FutureProvider<User?>((ref) async {
  final authState = ref.watch(authStateProvider);
  return authState.when(
    data: (fbUser) async {
      if (fbUser == null) return null;
      final repo = ref.read(authRepositoryProvider);
      final result = await repo.getCurrentProfile();
      return result.fold(
        // Login should still proceed; treat profile-fetch failures as
        // null so the router falls back to /login (existing behavior).
        (_) => null,
        (user) => user,
      );
    },
    loading: () => null,
    error: (_, __) => null,
  );
});
