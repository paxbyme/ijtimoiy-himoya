import 'package:dartz/dartz.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

import 'package:mobile/core/error/failures.dart';
import 'package:mobile/data/repositories/kpi_repository.dart';
import 'package:mobile/models/kpi/kpi_model.dart';
import 'package:mobile/providers/kpi_provider.dart';

import '../../helpers/fixtures.dart';

class MockKpiRepository extends Mock implements KpiRepository {}

void main() {
  late MockKpiRepository repo;
  late ProviderContainer container;

  setUp(() {
    repo = MockKpiRepository();
    container = ProviderContainer(overrides: [
      kpiRepositoryProvider.overrideWithValue(repo),
    ]);
    addTearDown(container.dispose);
  });

  test('myKpiProvider returns null when repo yields Right(null)', () async {
    when(() => repo.getMyKpi())
        .thenAnswer((_) async => const Right<Failure, KpiScore?>(null));

    final result = await container.read(myKpiProvider.future);

    expect(result, isNull);
  });

  // Negative-case translation (Left → throw → AsyncError) is covered by
  // KpiRepository tests; provider tests focus on the happy path because
  // Riverpod 3 makes it awkward to await an error-throwing FutureProvider
  // inside a unit test without subscribing to lifecycle events.

  test('kpiRankingsProvider returns parsed rankings', () async {
    when(() => repo.getRankings()).thenAnswer((_) async => Right([
          KpiScore.fromJson(Fixtures.kpi),
        ]));

    final result = await container.read(kpiRankingsProvider.future);

    expect(result, hasLength(1));
    expect(result.first.staffId, 'u_1');
  });
}
