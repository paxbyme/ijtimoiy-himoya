import 'dart:async';

import 'package:dartz/dartz.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

import 'package:mobile/core/error/failures.dart';
import 'package:mobile/data/repositories/ai_repository.dart';
import 'package:mobile/providers/ai_provider.dart';

class MockAiRepository extends Mock implements AiRepository {}

void main() {
  late MockAiRepository repository;
  late ProviderContainer container;

  setUp(() {
    repository = MockAiRepository();
    container = ProviderContainer(
      overrides: [aiRepositoryProvider.overrideWithValue(repository)],
    );
    addTearDown(container.dispose);
  });

  test(
    'exposes stream status and ignores a second concurrent message',
    () async {
      final stream = StreamController<Map<String, dynamic>>();
      addTearDown(stream.close);
      when(
        () => repository.sendMessageStream('first', null),
      ).thenAnswer((_) => stream.stream);

      final notifier = container.read(aiChatProvider.notifier);
      final sendFuture = notifier.sendMessage('first');
      await notifier.sendMessage('second');

      stream.add({'type': 'status', 'message': 'Manbalar qidirilmoqda...'});
      await pumpEventQueue();

      expect(notifier.isLoading, isTrue);
      expect(notifier.statusMessage, 'Manbalar qidirilmoqda...');
      verifyNever(() => repository.sendMessageStream('second', any()));

      stream.add({'type': 'meta', 'conversationId': 'conversation-1'});
      stream.add({'type': 'token', 'text': 'Tayyor javob'});
      stream.add({'type': 'done'});
      await stream.close();
      await sendFuture;

      final messages = container.read(aiChatProvider);
      expect(messages.map((message) => message.content), [
        'first',
        'Tayyor javob',
      ]);
      expect(messages.map((message) => message.messageIndex), [0, 1]);
      expect(notifier.isLoading, isFalse);
      expect(notifier.statusMessage, isEmpty);
      expect(notifier.conversationId, 'conversation-1');
    },
  );

  test(
    'replaces a partial streamed reply with the fallback response',
    () async {
      when(() => repository.sendMessageStream('question', null)).thenAnswer(
        (_) => Stream<Map<String, dynamic>>.multi((controller) {
          controller.add({'type': 'token', 'text': 'partial'});
          controller.addError(Exception('connection lost'));
        }),
      );
      when(() => repository.sendMessage('question', null)).thenAnswer(
        (_) async => const Right<Failure, Map<String, dynamic>>({
          'conversationId': 'conversation-2',
          'response': 'complete fallback',
        }),
      );

      final notifier = container.read(aiChatProvider.notifier);
      await notifier.sendMessage('question');

      final messages = container.read(aiChatProvider);
      expect(messages.map((message) => message.content), [
        'question',
        'complete fallback',
      ]);
      expect(
        messages.where((message) => message.content == 'partial'),
        isEmpty,
      );
      expect(notifier.isLoading, isFalse);
      expect(notifier.statusMessage, isEmpty);
    },
  );
}
