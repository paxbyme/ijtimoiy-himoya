import 'package:dio/dio.dart';
import 'package:mocktail/mocktail.dart';

import 'package:mobile/core/network/network_info.dart';
import 'package:mobile/data/datasources/local/auth_local_datasource.dart';
import 'package:mobile/data/datasources/remote/admin_remote_datasource.dart';
import 'package:mobile/data/datasources/remote/ai_remote_datasource.dart';
import 'package:mobile/data/datasources/remote/auth_remote_datasource.dart';
import 'package:mobile/data/datasources/remote/kpi_remote_datasource.dart';
import 'package:mobile/data/datasources/remote/task_remote_datasource.dart';

class MockNetworkInfo extends Mock implements NetworkInfo {}

class MockDio extends Mock implements Dio {}

class MockAuthLocalDataSource extends Mock implements AuthLocalDataSource {}

class MockAuthRemoteDataSource extends Mock implements AuthRemoteDataSource {}

class MockTaskRemoteDataSource extends Mock implements TaskRemoteDataSource {}

class MockKpiRemoteDataSource extends Mock implements KpiRemoteDataSource {}

class MockAiRemoteDataSource extends Mock implements AiRemoteDataSource {}

class MockAdminRemoteDataSource extends Mock implements AdminRemoteDataSource {}
