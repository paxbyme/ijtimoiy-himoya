import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

import 'package:mobile/core/error/failures.dart';
import 'package:mobile/data/repositories/ai_repository.dart';

import '../../helpers/mocks.dart';

void main() {
  late AiRepository repository;
  late MockAiRemoteDataSource remote;
  late MockNetworkInfo network;

  setUp(() {
    remote = MockAiRemoteDataSource();
    network = MockNetworkInfo();
    repository = AiRepository(remote, network);
  });

  group('uploadKnowledgeDocument', () {
    test('uploads the selected file when online', () async {
      when(() => network.isConnected).thenAnswer((_) async => true);
      when(
        () => remote.uploadKnowledgeDocument('/tmp/policy.pdf', 'policy.pdf'),
      ).thenAnswer((_) async {});

      final result = await repository.uploadKnowledgeDocument(
        '/tmp/policy.pdf',
        'policy.pdf',
      );

      expect(result.isRight(), isTrue);
      verify(
        () => remote.uploadKnowledgeDocument('/tmp/policy.pdf', 'policy.pdf'),
      ).called(1);
    });

    test(
      'returns NetworkFailure without calling the datasource offline',
      () async {
        when(() => network.isConnected).thenAnswer((_) async => false);

        final result = await repository.uploadKnowledgeDocument(
          '/tmp/policy.pdf',
          'policy.pdf',
        );

        result.fold(
          (failure) => expect(failure, isA<NetworkFailure>()),
          (_) => fail('expected Left'),
        );
        verifyNever(
          () => remote.uploadKnowledgeDocument('/tmp/policy.pdf', 'policy.pdf'),
        );
      },
    );
  });
}
