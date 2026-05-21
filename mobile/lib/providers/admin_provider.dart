import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/datasources/remote/admin_remote_datasource.dart';
import '../data/repositories/admin_repository.dart';
import '../models/auth/user_model.dart';
import '../models/task/department_model.dart';
import 'auth_provider.dart';

final adminRemoteDataSourceProvider = Provider<AdminRemoteDataSource>(
    (ref) => AdminRemoteDataSource(ref.read(dioProvider)));

final adminRepositoryProvider = Provider<AdminRepository>((ref) {
  return AdminRepository(
    ref.read(adminRemoteDataSourceProvider),
    ref.read(networkInfoProvider),
  );
});

final adminManagersProvider = FutureProvider<List<User>>((ref) async {
  final result = await ref.read(adminRepositoryProvider).getManagers();
  return result.fold((f) => throw f, (list) => list);
});

final adminDepartmentsProvider =
    FutureProvider<List<Department>>((ref) async {
  final result = await ref.read(adminRepositoryProvider).getDepartments();
  return result.fold((f) => throw f, (list) => list);
});
