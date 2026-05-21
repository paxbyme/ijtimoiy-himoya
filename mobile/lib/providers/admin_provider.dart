import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/auth/user_model.dart';
import '../models/task/department_model.dart';
import 'auth_provider.dart';

final adminManagersProvider = FutureProvider<List<User>>((ref) async {
  return ref.read(apiServiceProvider).getManagers();
});

final adminDepartmentsProvider = FutureProvider<List<Department>>((ref) async {
  return ref.read(apiServiceProvider).getDepartments();
});
