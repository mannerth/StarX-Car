import 'package:starx_car_common/starx_car_common.dart';

class PassengerRepository {
  const PassengerRepository(this._apiClient);
  final ApiClient _apiClient;

  Future<UserProfile> getProfile() async => UserProfile.fromJson(
    await _apiClient.get('/users/me') as Map<String, dynamic>,
  );

  Future<Map<String, dynamic>> getPassengerProfile() async =>
      await _apiClient.get('/passengers/me') as Map<String, dynamic>;

  Future<void> updatePhoneNumber(String phoneNumber) async {
    await _apiClient.put('/passengers/me', data: {'phoneNumber': phoneNumber});
  }

  Future<UserProfile> updateProfile({
    required String username,
    required String email,
    required String gender,
  }) async => UserProfile.fromJson(
    await _apiClient.put(
          '/users/me',
          data: {'username': username, 'email': email, 'gender': gender},
        )
        as Map<String, dynamic>,
  );

  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    await _apiClient.post(
      '/users/me/password',
      data: {'currentPassword': currentPassword, 'newPassword': newPassword},
    );
  }
}
