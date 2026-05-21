import 'package:dartz/dartz.dart';
import 'package:firebase_auth/firebase_auth.dart' as fb;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

import 'package:mobile/core/error/exceptions.dart';
import 'package:mobile/core/error/failures.dart';
import 'package:mobile/data/repositories/auth_repository.dart';
import 'package:mobile/models/auth/user_model.dart';

import '../../helpers/fixtures.dart';
import '../../helpers/mocks.dart';

class _MockFirebaseUser extends Mock implements fb.User {}

void main() {
  late AuthRepository repository;
  late MockAuthLocalDataSource local;
  late MockAuthRemoteDataSource remote;
  late MockNetworkInfo network;

  setUp(() {
    local = MockAuthLocalDataSource();
    remote = MockAuthRemoteDataSource();
    network = MockNetworkInfo();
    repository = AuthRepository(local, remote, network);
  });

  group('signIn', () {
    test('returns NetworkFailure when offline', () async {
      when(() => network.isConnected).thenAnswer((_) async => false);

      final result = await repository.signIn('998901234567', 'pw');

      expect(result, const Left(NetworkFailure()));
      verifyNever(() => local.signInWithPhone(any(), any()));
    });

    test('maps AuthException to AuthFailure with code', () async {
      when(() => network.isConnected).thenAnswer((_) async => true);
      when(() => local.signInWithPhone(any(), any())).thenThrow(
        const AuthException('Wrong creds', code: 'invalid-credential'),
      );

      final result = await repository.signIn('998901234567', 'pw');

      result.fold(
        (f) {
          expect(f, isA<AuthFailure>());
          expect((f as AuthFailure).code, 'invalid-credential');
        },
        (_) => fail('expected Left'),
      );
    });
  });

  group('getCurrentProfile', () {
    test('returns Right(null) when no Firebase user is signed in', () async {
      when(() => local.currentUser).thenReturn(null);

      final result = await repository.getCurrentProfile();

      expect(result, const Right<Failure, User?>(null));
      verifyNever(() => remote.getCurrentProfile());
    });

    test('hits the remote when a Firebase user exists', () async {
      when(() => local.currentUser).thenReturn(_MockFirebaseUser());
      when(() => network.isConnected).thenAnswer((_) async => true);
      final user = User.fromJson(Fixtures.user);
      when(() => remote.getCurrentProfile()).thenAnswer((_) async => user);

      final result = await repository.getCurrentProfile();

      result.fold(
        (_) => fail('expected Right'),
        (u) => expect(u?.id, 'u_1'),
      );
    });
  });
}
