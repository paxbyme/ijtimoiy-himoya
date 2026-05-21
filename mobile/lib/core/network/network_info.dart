import 'package:connectivity_plus/connectivity_plus.dart';

/// Lightweight connectivity check used by repositories before hitting
/// the network. Repositories short-circuit with `NetworkFailure` when
/// [isConnected] is false instead of letting Dio time out.
class NetworkInfo {
  final Connectivity _connectivity;

  NetworkInfo([Connectivity? connectivity])
      : _connectivity = connectivity ?? Connectivity();

  Future<bool> get isConnected async {
    final results = await _connectivity.checkConnectivity();
    return results.any((r) => r != ConnectivityResult.none);
  }
}
