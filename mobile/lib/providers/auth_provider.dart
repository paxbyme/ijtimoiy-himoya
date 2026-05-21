import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/auth_service.dart';
import '../services/api_service.dart';
import '../services/firestore_service.dart';
import '../models/auth/user_model.dart';

final authServiceProvider = Provider((ref) => AuthService());
final apiServiceProvider = Provider((ref) => ApiService());
final firestoreServiceProvider = Provider((ref) => FirestoreService());

final authStateProvider = StreamProvider((ref) {
  return ref.watch(authServiceProvider).authStateChanges;
});

final userProfileProvider = FutureProvider<User?>((ref) async {
  final authState = ref.watch(authStateProvider);
  return authState.when(
    data: (user) async {
      if (user == null) return null;
      try {
        return await ref.read(apiServiceProvider).getUserProfile();
      } catch (e) {
        return null;
      }
    },
    loading: () => null,
    error: (_, __) => null,
  );
});
