import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/datasources/remote/kpi_remote_datasource.dart';
import '../data/repositories/kpi_repository.dart';
import '../models/kpi/kpi_model.dart';
import 'auth_provider.dart';

final kpiRemoteDataSourceProvider = Provider<KpiRemoteDataSource>(
    (ref) => KpiRemoteDataSource(ref.read(dioProvider)));

final kpiRepositoryProvider = Provider<KpiRepository>((ref) {
  return KpiRepository(
    ref.read(kpiRemoteDataSourceProvider),
    ref.read(networkInfoProvider),
  );
});

final myKpiProvider = FutureProvider<KpiScore?>((ref) async {
  final result = await ref.read(kpiRepositoryProvider).getMyKpi();
  return result.fold((f) => throw f, (kpi) => kpi);
});

final kpiRankingsProvider = FutureProvider<List<KpiScore>>((ref) async {
  final result = await ref.read(kpiRepositoryProvider).getRankings();
  return result.fold((f) => throw f, (list) => list);
});
