import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';
import '../../core/providers.dart';

final driverAuthProvider =
    AsyncNotifierProvider<DriverAuthController, AuthSession?>(
      DriverAuthController.new,
    );

class DriverAuthController extends AsyncNotifier<AuthSession?> {
  @override
  Future<AuthSession?> build() async {
    final session = await ref.read(sessionStoreProvider).read();
    return session?.userType == 'driver' ? session : null;
  }

  Future<void> login(String username, String password) => _authenticate(
    '/auth/login',
    {'username': username, 'password': password},
  );
  Future<void> register(Map<String, Object> request) =>
      _authenticate('/auth/drivers/register', request);
  Future<void> _authenticate(String path, Map<String, Object> request) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final data =
          await ref.read(apiClientProvider).post(path, data: request)
              as Map<String, dynamic>;
      final user = data['user'] as Map<String, dynamic>;
      if (user['type'] != 'driver') throw const ApiException('该账号不是司机账号。');
      final session = AuthSession(
        accessToken: data['accessToken'] as String,
        refreshToken: data['refreshToken'] as String,
        userId: (user['id'] as num).toInt(),
        userType: 'driver',
      );
      await ref.read(sessionStoreProvider).save(session);
      return session;
    });
  }

  Future<void> logout() async {
    await ref.read(sessionStoreProvider).clear();
    state = const AsyncData(null);
  }
}
