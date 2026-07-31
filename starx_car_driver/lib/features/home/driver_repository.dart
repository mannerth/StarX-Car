import 'package:starx_car_common/starx_car_common.dart';

class DriverRepository {
  const DriverRepository(this._api);
  final ApiClient _api;
  Future<UserProfile> getUser() async =>
      UserProfile.fromJson(await _api.get('/users/me') as Map<String, dynamic>);
  Future<Map<String, dynamic>> getDriver() async =>
      await _api.get('/drivers/me') as Map<String, dynamic>;
  Future<Map<String, dynamic>?> getCar() async {
    try {
      return await _api.get('/cars/me') as Map<String, dynamic>;
    } on ApiException catch (error) {
      if (error.statusCode == 404) return null;
      rethrow;
    }
  }

  Future<void> updateUser(Map<String, Object> data) async =>
      _api.put('/users/me', data: data);
  Future<void> updateDriver(Map<String, Object> data) async =>
      _api.put('/drivers/me', data: data);
  Future<void> changePassword(String current, String fresh) async => _api.post(
    '/users/me/password',
    data: {'currentPassword': current, 'newPassword': fresh},
  );
  Future<void> saveCar(Map<String, Object> data, int? carId) async {
    if (carId == null) {
      await _api.post('/cars', data: data);
    } else {
      await _api.put('/cars/$carId', data: data);
    }
  }
}
