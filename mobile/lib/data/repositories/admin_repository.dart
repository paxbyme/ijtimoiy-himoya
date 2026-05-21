import 'package:dartz/dartz.dart';

import '../../core/error/failures.dart';
import '../../core/network/network_info.dart';
import '../../models/auth/user_model.dart';
import '../../models/kpi/manager_stats_model.dart';
import '../../models/task/department_model.dart';
import '../datasources/remote/admin_remote_datasource.dart';

/// Covers both the manager-facing staff endpoints and the developer-only
/// `/admin/*` endpoints. The split is by HTTP route, not by who calls it.
class AdminRepository {
  final AdminRemoteDataSource _remote;
  final NetworkInfo _network;

  AdminRepository(this._remote, this._network);

  // Staff
  Future<Either<Failure, List<User>>> getStaffList() =>
      _guard(_remote.getStaffList);
  Future<Either<Failure, User>> createStaff(Map<String, dynamic> data) =>
      _guard(() => _remote.createStaff(data));

  // Managers
  Future<Either<Failure, List<User>>> getManagers() =>
      _guard(_remote.getManagers);
  Future<Either<Failure, User>> createManager(Map<String, dynamic> data) =>
      _guard(() => _remote.createManager(data));
  Future<Either<Failure, User>> updateManager(
          String id, Map<String, dynamic> data) =>
      _guard(() => _remote.updateManager(id, data));
  Future<Either<Failure, void>> deactivateManager(String id) =>
      _guard(() => _remote.deactivateManager(id));
  Future<Either<Failure, void>> hardDeleteManager(String id) =>
      _guard(() => _remote.hardDeleteManager(id));
  Future<Either<Failure, ManagerStats>> getManagerStats(String id) =>
      _guard(() => _remote.getManagerStats(id));

  // Departments
  Future<Either<Failure, List<Department>>> getDepartments() =>
      _guard(_remote.getDepartments);
  Future<Either<Failure, Department>> createDepartment(
          Map<String, dynamic> data) =>
      _guard(() => _remote.createDepartment(data));
  Future<Either<Failure, Department>> updateDepartment(
          String id, Map<String, dynamic> data) =>
      _guard(() => _remote.updateDepartment(id, data));
  Future<Either<Failure, void>> deleteDepartment(String id) =>
      _guard(() => _remote.deleteDepartment(id));

  Future<Either<Failure, T>> _guard<T>(Future<T> Function() op) async {
    if (!await _network.isConnected) return const Left(NetworkFailure());
    try {
      return Right(await op());
    } catch (e) {
      return Left(FailureMapper.fromException(e));
    }
  }
}
