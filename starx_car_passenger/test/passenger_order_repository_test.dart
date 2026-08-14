import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';
import 'package:starx_car_passenger/features/orders/passenger_order_repository.dart';
import 'package:starx_car_passenger/features/profile/passenger_repository.dart';

void main() {
  test('quote serializes GCJ-02 places and parses the fare envelope', () async {
    final api = RecordingApiClient({
      'POST /orders/quote': {
        'pricingRuleId': 3,
        'pricingRuleVersion': 2,
        'estimatedDistanceM': 4200,
        'estimatedDurationS': 900,
        'fare': _fare(),
      },
    });
    final repository = PassengerOrderRepository(api);

    final quote = await repository.quote(
      pickup: _place('上车点', 31.23456789, 121.45678912),
      destination: _place('目的地', 31.21, 121.42),
    );

    expect(quote.estimatedDistanceM, 4200);
    expect(quote.fare.totalFare, '18.50');
    expect(api.lastPath, 'POST /orders/quote');
    final body = api.lastData as Map<String, dynamic>;
    expect((body['pickup'] as Map<String, dynamic>)['latitude'], 31.234568);
    expect((body['pickup'] as Map<String, dynamic>)['longitude'], 121.456789);
    expect((body['destination'] as Map<String, dynamic>)['longitude'], 121.42);
  });

  test('quote forwards an optional cancellation token', () async {
    final api = RecordingApiClient({
      'POST /orders/quote': {
        'pricingRuleId': 3,
        'pricingRuleVersion': 2,
        'estimatedDistanceM': 4200,
        'estimatedDurationS': 900,
        'fare': _fare(),
      },
    });
    final cancelToken = CancelToken();

    await PassengerOrderRepository(api).quote(
      pickup: _place('上车点', 31.2, 121.4),
      destination: _place('目的地', 31.21, 121.42),
      cancelToken: cancelToken,
    );

    expect(api.calls.single.cancelToken, same(cancelToken));
  });

  test('list sends status filter and parses server records', () async {
    final api = RecordingApiClient({
      'GET /passengers/me/orders': {
        'page': 2,
        'size': 10,
        'total': 11,
        'records': [
          {
            'orderNo': 'ORD-2',
            'status': 'ACCEPTED',
            'pickupName': '上车点',
            'destinationName': '目的地',
            'totalFare': '9.90',
            'createdTime': '2026-08-06T10:00:00Z',
            'estimatedFare': true,
          },
        ],
      },
    });

    final page = await PassengerOrderRepository(
      api,
    ).list(page: 2, size: 10, status: OrderStatus.accepted);

    expect(page.records.single.status, OrderStatus.accepted);
    expect(page.records.single.totalFare, '9.90');
    expect(api.lastQuery, {'page': 2, 'size': 10, 'status': 'ACCEPTED'});
  });

  test('list forwards an optional cancellation token', () async {
    final api = RecordingApiClient({
      'GET /passengers/me/orders': {
        'page': 1,
        'size': 20,
        'total': 0,
        'records': const [],
      },
    });
    final cancelToken = CancelToken();

    await PassengerOrderRepository(api).list(cancelToken: cancelToken);

    expect(api.calls.single.cancelToken, same(cancelToken));
  });

  test(
    'create parses the server order number and initial pending state',
    () async {
      final api = RecordingApiClient({
        'POST /orders': {
          'orderNo': 'ORD-NEW',
          'status': 'PENDING',
          'quote': {
            'pricingRuleId': 3,
            'pricingRuleVersion': 2,
            'estimatedDistanceM': 4200,
            'estimatedDurationS': 900,
            'fare': _fare(),
          },
        },
      });

      final created = await PassengerOrderRepository(api).create(
        pickup: _place('上车点', 31.2, 121.4),
        destination: _place('目的地', 31.21, 121.42),
      );

      expect(created.orderNo, 'ORD-NEW');
      expect(created.status, OrderStatus.pending);
      expect(created.quote.fare.totalFare, '18.50');
    },
  );

  test(
    'cancel trims a reason and route context preserves stale location',
    () async {
      final api = RecordingApiClient({
        'GET /orders/ORD-3/route-context': {
          'driverLocation': {
            'latitude': 31.2,
            'longitude': 121.4,
            'stale': true,
            'recordedTime': '2026-08-06T10:00:00Z',
          },
          'destinationName': '目的地',
          'destinationAddress': '道路',
          'destinationLat': 31.21,
          'destinationLng': 121.42,
          'recommendedRefreshDistanceM': 100,
          'recommendedRefreshSeconds': 60,
        },
      });
      final repository = PassengerOrderRepository(api);

      await repository.cancel('ORD-3', reason: '  临时有事  ');
      final context = await repository.routeContext('ORD-3');

      expect(api.calls.first.data, {'reason': '临时有事'});
      expect(context.driverLocation.stale, isTrue);
      expect(context.recommendedRefreshDistanceM, 100);
    },
  );

  test('delete account calls the passenger soft-delete endpoint', () async {
    final api = RecordingApiClient({});

    await PassengerRepository(api).deleteAccount();

    expect(api.lastPath, 'DELETE /passengers/me');
  });

  test('submitRating posts the star score and trimmed content', () async {
    final api = RecordingApiClient({
      'POST /orders/ORD-9/rating': {
        'rated': true,
        'rating': 5,
        'content': '服务很好',
        'createdAt': '2026-08-13 12:00:00',
      },
    });

    final rating = await PassengerOrderRepository(
      api,
    ).submitRating('ORD-9', rating: 5, content: ' 服务很好 ');

    expect(api.lastPath, 'POST /orders/ORD-9/rating');
    expect(api.lastData, {'rating': 5, 'content': '服务很好'});
    expect(rating.rated, isTrue);
    expect(rating.rating, 5);
    expect(rating.content, '服务很好');
    expect(rating.createdAt, DateTime(2026, 8, 13, 12));
  });

  test('submitRating omits blank content and rating query parses unrated', () async {
    final api = RecordingApiClient({
      'POST /orders/ORD-9/rating': {
        'rated': true,
        'rating': 3,
      },
      'GET /orders/ORD-9/rating': {
        'rated': false,
        'rating': null,
        'content': null,
        'createdAt': null,
      },
    });
    final repository = PassengerOrderRepository(api);

    await repository.submitRating('ORD-9', rating: 3, content: '   ');
    final current = await repository.rating('ORD-9');

    expect(
      (api.calls.first.data as Map<String, dynamic>).containsKey('content'),
      isFalse,
    );
    expect(current.rated, isFalse);
    expect(current.rating, isNull);
    expect(current.content, isNull);
  });

  test('myRatings sends page/size and parses the history records', () async {
    final api = RecordingApiClient({
      'GET /passengers/me/ratings': {
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

    final page = await PassengerOrderRepository(
      api,
    ).myRatings(page: 1, size: 20);

    expect(api.lastQuery, {'page': 1, 'size': 20});
    expect(page.total, 1);
    expect(page.records.single.orderNo, 'ORD-9');
    expect(page.records.single.rating, 5);
  });
}

Place _place(String name, double latitude, double longitude) => Place(
  name: name,
  location: GeoPoint(latitude: latitude, longitude: longitude),
);

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
  Map<String, dynamic>? get lastQuery => calls.last.query;

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
  Future<dynamic> delete(String path, {CancelToken? cancelToken}) async {
    calls.add(
      RecordedCall('DELETE', path, null, null, cancelToken: cancelToken),
    );
    return responses['DELETE $path'];
  }
}
