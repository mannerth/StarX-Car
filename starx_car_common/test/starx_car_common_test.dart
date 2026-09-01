import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';

void main() {
  test('API response recognises the backend success code', () {
    const response = ApiResponse<String>(
      code: 0,
      message: 'success',
      traceId: 'trace-001',
      data: 'payload',
    );

    expect(response.isSuccess, isTrue);
    expect(response.data, 'payload');
  });

  test('map points default to AMap GCJ-02 coordinates', () {
    const point = GeoPoint(latitude: 31.2304, longitude: 121.4737);

    expect(point.coordinateSystem, CoordinateSystem.gcj02);
  });

  test('location callbacks preserve GCJ-02 point and native metadata', () {
    final update = AmapLocationUpdate.fromRaw({
      'errorCode': '0',
      'latitude': '31.2304',
      'longitude': '121.4737',
      'accuracy': '8.5',
      'speed': '2.0',
      'bearing': '90',
      'locationTime': '2026-08-05 10:00:00',
    });

    expect(update.point?.coordinateSystem, CoordinateSystem.gcj02);
    expect(update.accuracyM, 8.5);
    expect(update.speedMps, 2.0);
    expect(update.bearing, 90);
    expect(update.hasError, isFalse);
  });

  test('order models preserve decimal money and tolerate unknown status', () {
    final summary = OrderSummary.fromJson({
      'orderNo': 'O2026080500000000000',
      'status': 'SERVER_ADDED_STATUS',
      'pickupName': '上车点',
      'destinationName': '目的地',
      'totalFare': 5.00,
      'createdTime': '2026-08-05 10:00:00',
      'estimatedFare': true,
    });

    expect(summary.status, OrderStatus.unknown);
    expect(summary.totalFare, '5.0');
    expect(summary.createdTime, isNotNull);
  });

  test('order models parse scheduled type and pickup time', () {
    final summary = OrderSummary.fromJson({
      'orderNo': 'O2026080500000000001',
      'status': 'SCHEDULED',
      'orderType': 'SCHEDULED',
      'scheduledPickupTime': '2026-08-05 10:00:00',
      'pickupName': '上车点',
      'destinationName': '目的地',
      'totalFare': '42.70',
      'createdTime': '2026-08-04 20:00:00',
      'estimatedFare': true,
    });

    expect(summary.status, OrderStatus.scheduled);
    expect(summary.orderType, OrderType.scheduled);
    expect(summary.isScheduled, isTrue);
    expect(summary.scheduledPickupTime, DateTime(2026, 8, 5, 10));

    final quote = OrderQuote.fromJson({
      'pricingRuleId': 1,
      'pricingRuleVersion': 1,
      'estimatedDistanceM': 12001,
      'estimatedDurationS': 1801,
      'fare': {
        'baseFare': '5.00',
        'distanceFare': '31.20',
        'drivingTimeFare': '6.50',
        'waitingFare': '0.00',
        'totalFare': '42.70',
      },
      'orderType': 'SCHEDULED',
      'scheduledPickupTime': '2026-08-05 10:00:00',
    });

    expect(quote.orderType, OrderType.scheduled);
    expect(quote.isScheduled, isTrue);
    expect(quote.scheduledPickupTime, DateTime(2026, 8, 5, 10));
  });

  test('order detail parses scheduled fields and keeps old format safe', () {
    final detail = OrderDetail.fromJson({
      'orderNo': 'O-RES-1',
      'status': 'SCHEDULED',
      'orderType': 'SCHEDULED',
      'scheduledPickupTime': '2026-08-05 10:00:00',
      'dispatchTime': '2026-08-05 09:30:00',
      'pickupPoiId': null,
      'pickupName': '起点',
      'pickupAddress': null,
      'pickupLat': '31.2304',
      'pickupLng': '121.4737',
      'destinationPoiId': null,
      'destinationName': '终点',
      'destinationAddress': null,
      'destinationLat': '31.2400',
      'destinationLng': '121.4800',
      'estimatedDistanceM': 1200,
      'estimatedDurationS': 600,
      'billingDistanceM': 0,
      'actualDurationS': null,
      'waitingDurationS': null,
      'fare': {
        'baseFare': '5.00',
        'distanceFare': '0.00',
        'drivingTimeFare': '2.00',
        'waitingFare': '0.00',
        'totalFare': '7.00',
      },
      'estimatedFare': true,
      'driver': null,
      'events': [
        {
          'eventType': 'CREATE',
          'fromStatus': null,
          'toStatus': 'SCHEDULED',
          'operatorType': 'PASSENGER',
          'createdTime': '2026-08-04 20:00:00',
          'remark': '预约叫车',
        },
      ],
    });

    expect(detail.status, OrderStatus.scheduled);
    expect(detail.isScheduled, isTrue);
    expect(detail.scheduledPickupTime, DateTime(2026, 8, 5, 10));
    expect(detail.dispatchTime, DateTime(2026, 8, 5, 9, 30));
    expect(detail.events.single.toStatus, 'SCHEDULED');

    final legacy = OrderDetail.fromJson({
      'orderNo': 'O-OLD-1',
      'status': 'PENDING',
      'pickupName': '起点',
      'pickupAddress': null,
      'pickupLat': '31.2304',
      'pickupLng': '121.4737',
      'destinationName': '终点',
      'destinationLat': '31.2400',
      'destinationLng': '121.4800',
      'estimatedDistanceM': 1200,
      'estimatedDurationS': 600,
      'billingDistanceM': 0,
      'fare': {
        'baseFare': '5.00',
        'distanceFare': '0.00',
        'drivingTimeFare': '2.00',
        'waitingFare': '0.00',
        'totalFare': '7.00',
      },
      'estimatedFare': true,
      'events': [],
    });
    expect(legacy.orderType, OrderType.unknown);
    expect(legacy.isScheduled, isFalse);
    expect(legacy.scheduledPickupTime, isNull);
  });

  test('driver location and route context use GCJ-02 and stale marker', () {
    final context = RouteContext.fromJson({
      'driverLocation': {
        'latitude': '39.916527',
        'longitude': '116.397128',
        'accuracyM': '8.50',
        'speedMps': null,
        'bearing': '180.00',
        'recordedTime': '2026-08-05 10:00:00',
        'receivedTime': '2026-08-05 10:00:03',
        'stale': true,
      },
      'destinationPoiId': 'B000A83M61',
      'destinationName': '北京首都国际机场',
      'destinationAddress': '机场路',
      'destinationLat': 40.079311,
      'destinationLng': 116.603112,
      'recommendedRefreshDistanceM': 100,
      'recommendedRefreshSeconds': 60,
    });

    expect(
      context.driverLocation.point.coordinateSystem,
      CoordinateSystem.gcj02,
    );
    expect(context.driverLocation.stale, isTrue);
    expect(context.destination.amapPoiId, 'B000A83M61');
    expect(context.recommendedRefreshDistanceM, 100);
  });

  test('order detail accepts a missing driver before assignment', () {
    final detail = OrderDetail.fromJson({
      'orderNo': 'O1',
      'status': 'PENDING',
      'pickupPoiId': null,
      'pickupName': '起点',
      'pickupAddress': null,
      'pickupLat': '31.2304',
      'pickupLng': '121.4737',
      'destinationPoiId': null,
      'destinationName': '终点',
      'destinationAddress': null,
      'destinationLat': '31.2400',
      'destinationLng': '121.4800',
      'estimatedDistanceM': 1200,
      'estimatedDurationS': 600,
      'billingDistanceM': 0,
      'actualDurationS': null,
      'waitingDurationS': null,
      'fare': {
        'baseFare': '5.00',
        'distanceFare': '0.00',
        'drivingTimeFare': '2.00',
        'waitingFare': '0.00',
        'totalFare': '7.00',
      },
      'estimatedFare': true,
      'driver': null,
      'events': [
        {
          'eventType': 'CREATE',
          'fromStatus': null,
          'toStatus': 'PENDING',
          'operatorType': 'PASSENGER',
          'createdTime': '2026-08-05 10:00:00',
          'remark': '即时叫车',
        },
      ],
    });

    expect(detail.driver, isNull);
    expect(detail.pickup.location.coordinateSystem, CoordinateSystem.gcj02);
    expect(detail.fare.totalFare, '7.00');
    expect(detail.events.single.eventType, 'CREATE');
    expect(detail.events.single.toStatus, 'PENDING');
  });

  test('native AMap route parser keeps GCJ-02 polyline points', () {
    final route = AmapDrivingRoute.fromJson({
      'distanceM': 1250,
      'durationS': 420,
      'points': [
        [31.2304, 121.4737],
        [31.2310, 121.4742],
      ],
    });

    expect(route.distanceM, 1250);
    expect(route.points, hasLength(2));
    expect(route.points.first.coordinateSystem, CoordinateSystem.gcj02);
  });

  test('walking route parser keeps GCJ-02 polyline points', () {
    final route = AmapWalkingRoute.fromJson({
      'distanceM': 320,
      'durationS': 240,
      'points': [
        [31.2304, 121.4737],
        [31.2312, 121.4741],
      ],
    });

    expect(route.distanceM, 320);
    expect(route.durationS, 240);
    expect(route.points, hasLength(2));
    expect(route.points.first.coordinateSystem, CoordinateSystem.gcj02);
  });

  test('order models parse terminated status and settlement', () {
    expect(orderStatusFromJson('TERMINATED'), OrderStatus.terminated);
    expect(orderStatusToJson(OrderStatus.terminated), 'TERMINATED');
    final result = OrderTerminationResult.fromJson({
      'orderNo': 'O-TERM',
      'status': 'TERMINATED',
      'actualDurationS': 60,
      'billingDistanceM': 100,
      'totalFare': '8.20',
    });
    expect(result.status, OrderStatus.terminated);
    expect(result.totalFare, '8.20');
  });

  test(
    'order detail keeps the passenger phone tail for driver verification',
    () {
      final detail = OrderDetail.fromJson({
        'orderNo': 'O2',
        'status': 'DRIVER_ARRIVED',
        'pickupPoiId': null,
        'pickupName': '起点',
        'pickupAddress': null,
        'pickupLat': '31.2304',
        'pickupLng': '121.4737',
        'destinationPoiId': null,
        'destinationName': '终点',
        'destinationAddress': null,
        'destinationLat': '31.2400',
        'destinationLng': '121.4800',
        'estimatedDistanceM': 1200,
        'estimatedDurationS': 600,
        'billingDistanceM': 0,
        'actualDurationS': null,
        'waitingDurationS': null,
        'fare': {
          'baseFare': '5.00',
          'distanceFare': '0.00',
          'drivingTimeFare': '2.00',
          'waitingFare': '0.00',
          'totalFare': '7.00',
        },
        'estimatedFare': true,
        'passengerPhoneTail': '1234',
        'driver': null,
        'events': [],
      });

      expect(detail.passengerPhoneTail, '1234');
    },
  );

  test('POI place serializes the native result for backend requests', () {
    const place = Place(
      name: '人民广场',
      address: '上海市黄浦区',
      amapPoiId: 'B000A1',
      location: GeoPoint(latitude: 31.2304, longitude: 121.4737),
    );

    expect(place.toJson(), {
      'poiId': 'B000A1',
      'name': '人民广场',
      'address': '上海市黄浦区',
      'latitude': 31.2304,
      'longitude': 121.4737,
    });
  });
}
