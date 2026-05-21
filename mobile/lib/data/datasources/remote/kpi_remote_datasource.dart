import 'package:dio/dio.dart';

import '../../../models/kpi/kpi_model.dart';

class KpiRemoteDataSource {
  final Dio _dio;
  KpiRemoteDataSource(this._dio);

  Future<KpiScore?> getMyKpi() async {
    final response = await _dio.get('/kpi/me');
    final data = response.data['data'];
    if (data == null) return null;
    return KpiScore.fromJson(data);
  }

  Future<List<KpiScore>> getRankings() async {
    final response = await _dio.get('/kpi/rankings');
    final List<dynamic> list = response.data['data'] ?? response.data;
    return list.map((e) => KpiScore.fromJson(e)).toList();
  }
}
