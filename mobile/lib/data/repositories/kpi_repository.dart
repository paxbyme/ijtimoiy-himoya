import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';
import '../../core/network/network_info.dart';
import '../../models/kpi/kpi_model.dart';
import '../datasources/remote/kpi_remote_datasource.dart';

class KpiRepository {
  final KpiRemoteDataSource _remote;
  final NetworkInfo _network;

  KpiRepository(this._remote, this._network);

  Future<Either<Failure, KpiScore?>> getMyKpi() => _guard(_remote.getMyKpi);

  Future<Either<Failure, List<KpiScore>>> getRankings() =>
      _guard(_remote.getRankings);

  Future<Either<Failure, T>> _guard<T>(Future<T> Function() op) async {
    if (!await _network.isConnected) return const Left(NetworkFailure());
    try {
      return Right(await op());
    } catch (e) {
      return Left(FailureMapper.fromException(e));
    }
  }
}
