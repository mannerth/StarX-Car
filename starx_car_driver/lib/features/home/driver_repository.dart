import 'package:starx_car_common/starx_car_common.dart';
import 'package:image_picker/image_picker.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';

final driverRepositoryProvider = Provider<DriverRepository>(
  (ref) => DriverRepository(ref.read(apiClientProvider)),
);

class DriverRepository {
  const DriverRepository(this._api);
  final ApiClient _api;
  Future<UserProfile> getUser() async =>
      UserProfile.fromJson(await _api.get('/users/me') as Map<String, dynamic>);
  Future<Map<String, dynamic>> getDriver() async =>
      await _api.get('/drivers/me') as Map<String, dynamic>;
  Future<List<Map<String, dynamic>>> getCars() async =>
      ((await _api.get('/drivers/me/cars') as List<dynamic>?) ?? const [])
          .whereType<Map<String, dynamic>>()
          .toList(growable: false);

  /// Returns the driver's current car (`driver.carId`) from the active
  /// bindings. A null result means the driver has no current vehicle.
  Future<Map<String, dynamic>?> getCar() async {
    final driver = await getDriver();
    final carId = (driver['carId'] as num?)?.toInt();
    if (carId == null) return null;
    final cars = await getCars();
    for (final car in cars) {
      if ((car['id'] as num?)?.toInt() == carId) return car;
    }
    return null;
  }

  Future<void> changeCurrentCar(int carId) async =>
      _api.put('/drivers/me/current-car', data: {'carId': carId});

  Future<void> updateWorkStatus(String status) async =>
      _api.put('/drivers/me/work-status', data: {'status': status});

  Future<void> reportLocation({
    required GeoPoint point,
    required DateTime recordedTime,
    double? accuracyM,
    double? speedMps,
    double? bearing,
    CancelToken? cancelToken,
  }) async {
    if (point.coordinateSystem != CoordinateSystem.gcj02) {
      throw StateError('司机位置上报必须使用 GCJ-02 坐标。');
    }
    await _api.put(
      '/drivers/me/location',
      data: {
        // The API and its DECIMAL(10,6) storage accept at most six
        // fractional digits. AMap may return more precision on some devices.
        'latitude': _apiCoordinate(point.latitude),
        'longitude': _apiCoordinate(point.longitude),
        ...?accuracyM == null ? null : {'accuracyM': accuracyM},
        ...?speedMps == null ? null : {'speedMps': speedMps},
        ...?bearing == null ? null : {'bearing': bearing},
        'recordedTime': _formatBackendTime(recordedTime),
      },
      cancelToken: cancelToken,
    );
  }

  Future<OrderPage> pendingOrders({
    int page = 1,
    int size = 20,
    CancelToken? cancelToken,
  }) async => OrderPage.fromJson(
    await _api.get(
          '/drivers/me/orders/pending',
          queryParameters: {'page': page, 'size': size},
          cancelToken: cancelToken,
        )
        as Map<String, dynamic>,
  );

  Future<OrderPage> orders({
    int page = 1,
    int size = 20,
    OrderStatus? status,
    CancelToken? cancelToken,
  }) async {
    final query = <String, dynamic>{'page': page, 'size': size};
    if (status != null && status != OrderStatus.unknown) {
      query['status'] = orderStatusToJson(status);
    }
    return OrderPage.fromJson(
      await _api.get(
            '/drivers/me/orders',
            queryParameters: query,
            cancelToken: cancelToken,
          )
          as Map<String, dynamic>,
    );
  }

  Future<OrderDetail> detail(String orderNo) async => OrderDetail.fromJson(
    await _api.get('/orders/$orderNo') as Map<String, dynamic>,
  );

  Future<void> acceptOrder(String orderNo) async =>
      _api.post('/orders/$orderNo/accept');
  Future<void> cancelOrder(String orderNo, {String? reason}) async => _api.post(
    '/orders/$orderNo/cancel',
    data: reason == null || reason.trim().isEmpty
        ? null
        : {'reason': reason.trim()},
  );
  Future<void> arrive(String orderNo) async =>
      _api.post('/orders/$orderNo/arrive');
  Future<void> startService(String orderNo, {required String phoneTail}) async =>
      _api.post('/orders/$orderNo/start-service', data: {'phoneTail': phoneTail});
  Future<OrderCompletionResult> complete(String orderNo) async =>
      OrderCompletionResult.fromJson(
        await _api.post('/orders/$orderNo/complete') as Map<String, dynamic>,
      );

  Future<void> updateUser(Map<String, Object?> data) async =>
      _api.put('/users/me', data: data);
  Future<void> updateAvatar(UserProfile user, XFile avatar) async {
    await _api.putMultipart(
      '/users/me',
      fields: {
        'username': user.username,
        'email': user.email ?? '',
        'gender': user.gender,
        'realName': user.realName ?? '',
        'avatarUrl': '',
      },
      fileBytes: await avatar.readAsBytes(),
      fileName: avatar.name,
    );
  }

  Future<void> updateDriver(Map<String, Object?> data) async =>
      _api.put('/drivers/me', data: data);
  Future<void> changePassword(String current, String fresh) async => _api.post(
    '/users/me/password',
    data: {'currentPassword': current, 'newPassword': fresh},
  );

  Future<void> deleteAccount() async {
    await _api.delete('/drivers/me');
  }

  Future<void> saveCar(Map<String, Object> data, int? carId) async {
    if (carId == null) {
      await _api.post('/cars', data: data);
    } else {
      await _api.put('/cars/$carId', data: data);
    }
  }
}

double _apiCoordinate(double value) => double.parse(value.toStringAsFixed(6));

String _formatBackendTime(DateTime value) {
  final local = value.toLocal();
  String two(int number) => number.toString().padLeft(2, '0');
  return '${local.year}-${two(local.month)}-${two(local.day)} '
      '${two(local.hour)}:${two(local.minute)}:${two(local.second)}';
}

