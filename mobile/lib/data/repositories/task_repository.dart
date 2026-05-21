import 'package:dartz/dartz.dart' hide Task;

import '../../core/error/failures.dart';
import '../../core/network/network_info.dart';
import '../../models/task/task_model.dart';
import '../datasources/remote/task_remote_datasource.dart';

class TaskRepository {
  final TaskRemoteDataSource _remote;
  final NetworkInfo _network;

  TaskRepository(this._remote, this._network);

  Future<Either<Failure, List<Task>>> getMyTasks() =>
      _guard(() => _remote.getMyTasks());

  Future<Either<Failure, List<Task>>> getAllTasks() =>
      _guard(() => _remote.getTasks());

  Future<Either<Failure, Task>> createTask(Map<String, dynamic> data) =>
      _guard(() => _remote.createTask(data));

  Future<Either<Failure, List<Task>>> createBulkTasks(
          List<String> assignedToList, Map<String, dynamic> data) =>
      _guard(() => _remote.createBulkTasks(assignedToList, data));

  Future<Either<Failure, Task>> completeTask(String taskId) =>
      _guard(() => _remote.completeTask(taskId));

  Future<Either<Failure, Task>> updateStatus(String taskId, String status) =>
      _guard(() => _remote.updateTaskStatus(taskId, status));

  Future<Either<Failure, Task>> acceptTask(String taskId) =>
      _guard(() => _remote.acceptTask(taskId));

  Future<Either<Failure, Task>> uploadAttachment(
          String taskId, String filePath, String fileName) =>
      _guard(() => _remote.uploadAttachment(taskId, filePath, fileName));

  Future<Either<Failure, T>> _guard<T>(Future<T> Function() op) async {
    if (!await _network.isConnected) return const Left(NetworkFailure());
    try {
      return Right(await op());
    } catch (e) {
      return Left(FailureMapper.fromException(e));
    }
  }
}
