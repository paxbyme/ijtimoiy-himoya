import 'package:dartz/dartz.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

import 'package:mobile/core/error/failures.dart';
import 'package:mobile/data/repositories/kpi_repository.dart';
import 'package:mobile/models/kpi/kpi_model.dart';

import '../../helpers/fixtures.dart';
import '../../helpers/mocks.dart';

void main() {
  late KpiRepository repository;
  late MockKpiRemoteDataSource remote;
  late MockNetworkInfo network;

  setUp(() {
    remote = MockKpiRemoteDataSource();
    network = MockNetworkInfo();
    repository = KpiRepository(remote, network);
  });

  test('getMyKpi returns null on Right when backend has no score', () async {
    when(() => network.isConnected).thenAnswer((_) async => true);
    when(() => remote.getMyKpi()).thenAnswer((_) async => null);

    final result = await repository.getMyKpi();

    expect(result, const Right<Failure, KpiScore?>(null));
  });

  test('getMyKpi forwards the parsed KpiScore', () async {
    when(() => network.isConnected).thenAnswer((_) async => true);
    final score = KpiScore.fromJson(Fixtures.kpi);
    when(() => remote.getMyKpi()).thenAnswer((_) async => score);

    final result = await repository.getMyKpi();

    result.fold(
      (_) => fail('expected Right'),
      (k) => expect(k?.score, 87),
    );
  });

  test('getRankings returns NetworkFailure when offline', () async {
    when(() => network.isConnected).thenAnswer((_) async => false);

    final result = await repository.getRankings();

    expect(result, const Left(NetworkFailure()));
    verifyNever(() => remote.getRankings());
  });
}
