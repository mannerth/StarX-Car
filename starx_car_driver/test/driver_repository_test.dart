import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';
import 'package:starx_car_driver/features/home/driver_repository.dart';

class RecordingRealtime extends RealtimeClient {
  RecordingRealtime(ApiClient api)
    : super(baseUrl: 'http://test/api', api: api);
  String? type;
  Map<String, dynamic>? data;
  @override
  Future<void> request(String type, Map<String, dynamic> data) async {
    this.type = type;
    this.data = data;
  }
}

void main() {
  test(
    'workbench location uses WebSocket with API decimal precision',
    () async {
      final api = RecordingApiClient({});
      final realtime = RecordingRealtime(api);
      await DriverRepository(api).reportLocation(
        point: const GeoPoint(latitude: 31.23456789, longitude: 121.45678912),
        recordedTime: DateTime(2026, 10, 18, 12),
        accuracyM: 8.567,
        realtime: realtime,
      );
      expect(realtime.type, 'driver.location');
      expect(realtime.data?['latitude'], 31.234568);
      expect(realtime.data?['longitude'], 121.456789);
      expect(realtime.data?['accuracyM'], 8.57);
      expect(api.calls, isEmpty);
    },
  );
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
      point: const GeoPoint(latitude: 31.23456789, longitude: 121.45678912),
      recordedTime: recorded,
      accuracyM: 4.567,
      speedMps: 2.126,
      bearing: 359.999,
    );

    expect(api.lastPath, 'PUT /drivers/me/location');
    expect(api.lastData, {
      'latitude': 31.234568,
      'longitude': 121.456789,
      'accuracyM': 4.57,
      'speedMps': 2.13,
      'bearing': 360.0,
      'recordedTime': '2026-08-06 12:03:04',
    });

    final cancelToken = CancelToken();
    await repository.reportLocation(
      point: const GeoPoint(latitude: 31.2, longitude: 121.4),
      recordedTime: recorded,
      accuracyM: double.nan,
      speedMps: -1,
      bearing: 361,
      cancelToken: cancelToken,
    );
    expect(api.calls.last.cancelToken, same(cancelToken));
    expect(api.calls.last.data, {
      'latitude': 31.2,
      'longitude': 121.4,
      'recordedTime': '2026-08-06 12:03:04',
    });

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
      await repository.startService('ORD-4', phoneTail: '1234');
      final result = await repository.complete('ORD-4');

      expect(api.calls.map((call) => '${call.method} ${call.path}'), [
        'POST /orders/ORD-4/accept',
        'POST /orders/ORD-4/arrive',
        'POST /orders/ORD-4/start-service',
        'POST /orders/ORD-4/complete',
      ]);
      expect(
        api.calls
            .firstWhere(
              (call) =>
                  '${call.method} ${call.path}' ==
                  'POST /orders/ORD-4/start-service',
            )
            .data,
        {'phoneTail': '1234'},
      );
      expect(result.waitingDurationS, 420);
      expect(result.fare.totalFare, '18.50');
    },
  );

  test('delete account calls the driver soft-delete endpoint', () async {
    final api = RecordingApiClient({});

    await DriverRepository(api).deleteAccount();

    expect(api.lastPath, 'DELETE /drivers/me');
  });

  test('terminate posts reason and parses server settlement', () async {
    final api = RecordingApiClient({
      'POST /orders/ORD-6/terminate': {
        'orderNo': 'ORD-6',
        'status': 'TERMINATED',
        'endedTime': '2026-08-06 12:10:00',
        'actualDurationS': 600,
        'billingDistanceM': 1200,
        'totalFare': '12.50',
      },
    });
    final result = await DriverRepository(api).terminateOrder(
      'ORD-6',
      reasonCode: 'DRIVER_REQUEST',
      reasonDetail: '临时结束',
    );
    expect(api.lastData, {
      'reasonCode': 'DRIVER_REQUEST',
      'reasonDetail': '临时结束',
    });
    expect(result.status, OrderStatus.terminated);
    expect(result.totalFare, '12.50');
  });

  test('complaint posts trimmed type and content', () async {
    final api = RecordingApiClient({});

    await DriverRepository(api).createComplaint(
      'ORD-6',
      complaintType: ' ORDER_SERVICE ',
      content: ' 服务问题 ',
    );

    expect(api.lastPath, 'POST /orders/ORD-6/complaints');
    expect(api.lastData, {'complaintType': 'ORDER_SERVICE', 'content': '服务问题'});
  });

  test('order detail forwards its cancellation token', () async {
    final api = RecordingApiClient({'GET /orders/ORD-5': _orderDetail()});
    final cancelToken = CancelToken();

    await DriverRepository(api).detail('ORD-5', cancelToken: cancelToken);

    expect(api.lastPath, 'GET /orders/ORD-5');
    expect(api.calls.last.cancelToken, same(cancelToken));
  });

  test(
    'myRatings sends page/size and parses the received rating history',
    () async {
      final api = RecordingApiClient({
        'GET /drivers/me/ratings': {
          'page': 1,
          'size': 20,
          'total': 1,
          'records': [
            {
              'orderNo': 'ORD-9',
              'rating': 5,
              'content': '服务很好',
              'createdAt': '2026-08-13 12:00:00',
            },
          ],
        },
      });

      final page = await DriverRepository(api).myRatings(page: 1, size: 20);

      expect(api.lastPath, 'GET /drivers/me/ratings');
      expect(api.calls.single.query, {'page': 1, 'size': 20});
      expect(page.total, 1);
      expect(page.records.single.orderNo, 'ORD-9');
      expect(page.records.single.rating, 5);
      expect(page.records.single.content, '服务很好');
    },
  );
}

Map<String, String> _fare() => {
  'baseFare': '5.00',
  'distanceFare': '12.00',
  'drivingTimeFare': '1.00',
  'waitingFare': '0.50',
  'totalFare': '18.50',
};

Map<String, dynamic> _orderDetail() => {
  'orderNo': 'ORD-5',
  'status': 'ACCEPTED',
  'pickupName': '上车点',
  'pickupLat': 31.2,
  'pickupLng': 121.4,
  'destinationName': '目的地',
  'destinationLat': 31.3,
  'destinationLng': 121.5,
  'estimatedDistanceM': 1000,
  'estimatedDurationS': 300,
  'billingDistanceM': 0,
  'fare': _fare(),
  'estimatedFare': true,
  'events': const [],
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
