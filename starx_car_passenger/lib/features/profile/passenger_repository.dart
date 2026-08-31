import 'package:starx_car_common/starx_car_common.dart';
import 'package:image_picker/image_picker.dart';

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

  Future<void> deleteAccount() async {
    await _apiClient.delete('/passengers/me');
  }

  Future<WalletBalance> walletBalance() => WalletApi(_apiClient).balance();

  Future<WalletBalance> recharge(double amount) =>
      WalletApi(_apiClient).recharge(amount);

  Future<UserProfile> updateProfile({
    required String username,
    required String email,
    required String gender,
    required String realName,
    XFile? avatar,
  }) async {
    final data =
        await _apiClient.putMultipart(
              '/users/me',
              fields: {
                'username': username,
                'email': email,
                'gender': gender,
                'realName': realName,
                'avatarUrl': '',
              },
              fileBytes: avatar == null ? null : await avatar.readAsBytes(),
              fileName: avatar?.name,
            )
            as Map<String, dynamic>;
    return UserProfile.fromJson(data);
  }

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
