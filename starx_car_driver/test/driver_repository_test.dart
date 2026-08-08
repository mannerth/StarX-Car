import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';
import 'package:starx_car_driver/features/home/driver_repository.dart';

void main() {
  test("getCar follows driver.carId across the driver's bound cars", () async {
    final api = RecordingApiClient({
      'GET /drivers/me': {'carId': 22},
      'GET /drivers/me/cars': [
        {'id': 11, 'plateNumber': '沪A11111'},
        {'id': 22, 'plateNumber': '沪A22222'},
      ],
    });

    final car = await DriverRepository(api).getCar();

    expect(car?['id'], 22);
    expect(car?['plateNumber'], '沪A22222');
  });

  test('location reporting accepts GCJ-02 and rejects WGS-84', () async {
    final api = RecordingApiClient({});
    final repository = DriverRepository(api);
    final recorded = DateTime(2026, 8, 6, 12, 3, 4);

    await repository.reportLocation(
      point: const GeoPoint(
        latitude: 31.23456789,
        longitude: 121.45678912,
      ),
      recordedTime: recorded,
      accuracyM: 4.5,
      speedMps: 2.1,
      bearing: 90,
    );

    expect(api.lastPath, 'PUT /drivers/me/location');
    expect(api.lastData, {
      'latitude': 31.234568,
      'longitude': 121.456789,
      'accuracyM': 4.5,
      'speedMps': 2.1,
      'bearing': 90,
      'recordedTime': '2026-08-06 12:03:04',
    });

    final cancelToken = CancelToken();
    await repository.reportLocation(
      point: const GeoPoint(latitude: 31.2, longitude: 121.4),
      recordedTime: recorded,
      cancelToken: cancelToken,
    );
    expect(api.calls.last.cancelToken, same(cancelToken));

    await expectLater(
      repository.reportLocation(
        point: const GeoPoint(
          latitude: 31.2,
          longitude: 121.4,
          coordinateSystem: CoordinateSystem.wgs84,
        ),
        recordedTime: recorded,
      ),
      throwsStateError,
    );
  });

  test(
    'status actions use role-specific endpoints and parse final settlement',
    () async {
      final api = RecordingApiClient({
        'POST /orders/ORD-4/complete': {
          'orderNo': 'ORD-4',
          'status': 'COMPLETED',
          'completedTime': '2026-08-06T12:10:00Z',
          'actualDurationS': 1800,
          'waitingDurationS': 420,
          'fare': _fare(),
        },
      });
      final repository = DriverRepository(api);

      await repository.acceptOrder('ORD-4');
      await repository.arrive('ORD-4');
      await repository.startService('ORD-4');
      final result = await repository.complete('ORD-4');

      expect(api.calls.map((call) => '${call.method} ${call.path}'), [
        'POST /orders/ORD-4/accept',
        'POST /orders/ORD-4/arrive',
        'POST /orders/ORD-4/start-service',
        'POST /orders/ORD-4/complete',
      ]);
      expect(result.waitingDurationS, 420);
      expect(result.fare.totalFare, '18.50');
    },
  );

  test('delete account calls the driver soft-delete endpoint', () async {
    final api = RecordingApiClient({});

    await DriverRepository(api).deleteAccount();

    expect(api.lastPath, 'DELETE /drivers/me');
  });
}

Map<String, String> _fare() => {
  'baseFare': '5.00',
  'distanceFare': '12.00',
  'drivingTimeFare': '1.00',
  'waitingFare': '0.50',
  'totalFare': '18.50',
};

class RecordedCall {
  RecordedCall(
    this.method,
    this.path,
    this.data,
    this.query, {
    this.cancelToken,
  });

  final String method;
  final String path;
  final Object? data;
  final Map<String, dynamic>? query;
  final CancelToken? cancelToken;
}

class RecordingApiClient extends ApiClient {
  RecordingApiClient(this.responses)
    : super(baseUrl: 'http://test', sessionStore: SessionStore());

  final Map<String, dynamic> responses;
  final calls = <RecordedCall>[];

  String get lastPath => '${calls.last.method} ${calls.last.path}';
  Object? get lastData => calls.last.data;

  @override
  Future<dynamic> get(
    String path, {
    Map<String, dynamic>? queryParameters,
    CancelToken? cancelToken,
  }) async {
    calls.add(
      RecordedCall(
        'GET',
        path,
        null,
        queryParameters,
        cancelToken: cancelToken,
      ),
    );
    return responses['GET $path'];
  }

  @override
  Future<dynamic> post(
    String path, {
    Object? data,
    CancelToken? cancelToken,
  }) async {
    calls.add(RecordedCall('POST', path, data, null, cancelToken: cancelToken));
    return responses['POST $path'];
  }

  @override
  Future<dynamic> put(
    String path, {
    Object? data,
    CancelToken? cancelToken,
  }) async {
    calls.add(RecordedCall('PUT', path, data, null, cancelToken: cancelToken));
    return responses['PUT $path'];
  }

  @override
  Future<dynamic> delete(String path, {CancelToken? cancelToken}) async {
    calls.add(
      RecordedCall('DELETE', path, null, null, cancelToken: cancelToken),
    );
    return responses['DELETE $path'];
  }
}
