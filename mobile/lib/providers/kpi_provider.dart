import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/kpi/kpi_model.dart';
import 'auth_provider.dart';

final myKpiProvider = FutureProvider<KpiScore?>((ref) async {
  final api = ref.read(apiServiceProvider);
  return api.getMyKpi();
});

final kpiRankingsProvider = FutureProvider<List<KpiScore>>((ref) async {
  final api = ref.read(apiServiceProvider);
  return api.getKpiRankings();
});
