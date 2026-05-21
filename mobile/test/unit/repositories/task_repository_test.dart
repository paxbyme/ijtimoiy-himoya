import 'package:dartz/dartz.dart' hide Task;
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

import 'package:mobile/core/error/failures.dart';
import 'package:mobile/data/repositories/task_repository.dart';
import 'package:mobile/models/task/task_model.dart';

import '../../helpers/fixtures.dart';
import '../../helpers/mocks.dart';

void main() {
  late TaskRepository repository;
  late MockTaskRemoteDataSource remote;
  late MockNetworkInfo network;

  setUp(() {
    remote = MockTaskRemoteDataSource();
    network = MockNetworkInfo();
    repository = TaskRepository(remote, network);
  });

  group('getMyTasks', () {
    test('returns Right(tasks) when remote succeeds', () async {
      when(() => network.isConnected).thenAnswer((_) async => true);
      final fixtureTasks = [Task.fromJson(Fixtures.task)];
      when(() => remote.getMyTasks()).thenAnswer((_) async => fixtureTasks);

      final result = await repository.getMyTasks();

      expect(result, isA<Right<Failure, List<Task>>>());
      result.fold(
        (_) => fail('expected Right'),
        (tasks) => expect(tasks.first.id, 't_1'),
      );
      verify(() => remote.getMyTasks()).called(1);
    });

    test('returns NetworkFailure when offline', () async {
      when(() => network.isConnected).thenAnswer((_) async => false);

      final result = await repository.getMyTasks();

      expect(result, const Left(NetworkFailure()));
      verifyNever(() => remote.getMyTasks());
    });

    test('maps DioException(500) to ServerFailure', () async {
      when(() => network.isConnected).thenAnswer((_) async => true);
      when(() => remote.getMyTasks()).thenThrow(
        DioException(
          requestOptions: RequestOptions(path: '/tasks'),
          type: DioExceptionType.badResponse,
          response: Response(
            requestOptions: RequestOptions(path: '/tasks'),
            statusCode: 500,
            data: {'message': 'boom'},
          ),
        ),
      );

      final result = await repository.getMyTasks();

      expect(result, isA<Left<Failure, List<Task>>>());
      result.fold(
        (f) {
          expect(f, isA<ServerFailure>());
          expect(f.message, 'boom');
        },
        (_) => fail('expected Left'),
      );
    });

    test('maps DioException(401) to AuthFailure', () async {
      when(() => network.isConnected).thenAnswer((_) async => true);
      when(() => remote.getMyTasks()).thenThrow(
        DioException(
          requestOptions: RequestOptions(path: '/tasks'),
          type: DioExceptionType.badResponse,
          response: Response(
            requestOptions: RequestOptions(path: '/tasks'),
            statusCode: 401,
            data: {'message': 'unauthorized'},
          ),
        ),
      );

      final result = await repository.getMyTasks();

      result.fold(
        (f) => expect(f, isA<AuthFailure>()),
        (_) => fail('expected Left'),
      );
    });
  });

  group('completeTask', () {
    test('passes taskId through to the data source', () async {
      when(() => network.isConnected).thenAnswer((_) async => true);
      final completed = Task.fromJson({...Fixtures.task, 'status': 'COMPLETED'});
      when(() => remote.completeTask('t_1'))
          .thenAnswer((_) async => completed);

      final result = await repository.completeTask('t_1');

      result.fold(
        (_) => fail('expected Right'),
        (t) => expect(t.status, 'COMPLETED'),
      );
    });
  });
}
