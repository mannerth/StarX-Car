import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';

import '../../core/providers.dart';

final passengerAuthProvider =
    AsyncNotifierProvider<PassengerAuthController, AuthSession?>(
      PassengerAuthController.new,
    );

class PassengerAuthController extends AsyncNotifier<AuthSession?> {
  @override
  Future<AuthSession?> build() async {
    final session = await ref.read(sessionStoreProvider).read();
    return session?.userType == 'passenger' ? session : null;
  }

  Future<void> login({required String username, required String password}) {
    return _authenticate('/auth/login', {
      'username': username,
      'password': password,
    });
  }

  Future<void> register({
    required String username,
    required String password,
    required String email,
    required String gender,
    required String phoneNumber,
    required String realName,
  }) {
    return _authenticate('/auth/passengers/register', {
      'username': username,
      'password': password,
      'email': email,
      'gender': gender,
      'phoneNumber': phoneNumber,
      'realName': realName,
    });
  }

  Future<void> _authenticate(String path, Map<String, Object> request) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final data =
          await ref.read(apiClientProvider).post(path, data: request)
              as Map<String, dynamic>;
      final user = data['user'] as Map<String, dynamic>;
      if (user['type'] != 'passenger') {
        throw const ApiException('该账号不是乘客账号。');
      }
      final session = AuthSession(
        accessToken: data['accessToken'] as String,
        refreshToken: data['refreshToken'] as String,
        userId: (user['id'] as num).toInt(),
        userType: 'passenger',
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
