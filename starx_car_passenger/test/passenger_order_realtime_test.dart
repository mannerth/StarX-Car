import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';
import 'package:starx_car_passenger/core/providers.dart';
import 'package:starx_car_passenger/features/auth/auth_controller.dart';
import 'package:starx_car_passenger/features/orders/order_realtime.dart';
import 'package:starx_car_passenger/features/orders/passenger_orders_page.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  for (final topic in ['order.route', 'order.location']) {
    test('$topic ends normally on an older server state conflict', () async {
      final realtime = FakeRealtime(expectedTopic: topic);
      final container = ProviderContainer();
      final source = Provider.autoDispose(
        (ref) => passengerOrderSnapshots(ref, realtime, topic, 'ORD-1'),
      );
      final providerSubscription = container.listen(source, (_, _) {});
      final errors = <Object>[];
      final done = Completer<void>();
      final subscription = container
          .read(source)
          .listen((_) {}, onError: errors.add, onDone: done.complete);
      addTearDown(() async {
        providerSubscription.close();
        container.dispose();
        await subscription.cancel();
        await realtime.dispose();
      });
      realtime.snapshots.addError(
        const ApiException('订单当前不可获取路线规划上下文', statusCode: 409),
      );
      await done.future.timeout(const Duration(seconds: 2));
      expect(errors, isEmpty);
      expect(realtime.snapshots.hasListener, isFalse);
    });
  }

  test('real route errors still reach the subscriber', () async {
    final realtime = FakeRealtime(expectedTopic: 'order.route');
    final container = ProviderContainer();
    final source = Provider.autoDispose(
      (ref) => passengerOrderSnapshots(ref, realtime, 'order.route', 'ORD-1'),
    );
    final providerSubscription = container.listen(source, (_, _) {});
    final error = Completer<Object>();
    final subscription = container
        .read(source)
        .listen((_) {}, onError: error.complete);
    addTearDown(() async {
      providerSubscription.close();
      container.dispose();
      await subscription.cancel();
      await realtime.dispose();
    });
    realtime.snapshots.addError(const ApiException('无权查看', statusCode: 403));
    expect(
      await error.future.timeout(const Duration(seconds: 2)),
      isA<ApiException>().having((e) => e.statusCode, 'status', 403),
    );
  });

  test(
    'disposal releases a subscription even while its consumer is paused',
    () async {
      final realtime = FakeRealtime(expectedTopic: 'order.route');
      final container = ProviderContainer();
      final source = Provider.autoDispose(
        (ref) => passengerOrderSnapshots(ref, realtime, 'order.route', 'ORD-1'),
      );
      container.listen(source, (_, _) {});
      final subscription = container.read(source).listen((_) {});
      subscription.pause();
      expect(realtime.snapshots.hasListener, isTrue);
      container.dispose();
      expect(realtime.snapshots.hasListener, isFalse);
      subscription.resume();
      await subscription.cancel();
      await realtime.dispose();
    },
  );
  test(
    'detail follows pushed states without polling and stops at completion',
    () async {
      final realtime = FakeRealtime();
      final container = ProviderContainer(
        overrides: [
          sessionStoreProvider.overrideWithValue(MemorySessionStore()),
          passengerRealtimeProvider.overrideWithValue(realtime),
        ],
      );
      addTearDown(container.dispose);
      addTearDown(realtime.dispose);
      await container.read(passengerAuthProvider.future);
      final statuses = <OrderStatus>[];
      final subscription = container.listen(
        passengerOrderDetailProvider('ORD-1'),
        (_, next) {
          next.whenData((detail) => statuses.add(detail.status));
        },
      );
      addTearDown(subscription.close);
      await Future<void>.delayed(const Duration(milliseconds: 30));
      for (final status in ['PENDING', 'ACCEPTED', 'COMPLETED']) {
        realtime.snapshots.add({
          'orderNo': 'ORD-1',
          'status': status,
          'pickupName': '上车点',
          'pickupLat': 31.2,
          'pickupLng': 121.4,
          'destinationName': '目的地',
          'destinationLat': 31.21,
          'destinationLng': 121.42,
          'estimatedDistanceM': 4200,
          'estimatedDurationS': 900,
          'billingDistanceM': 0,
          'fare': <String, dynamic>{},
        });
        await Future<void>.delayed(const Duration(milliseconds: 30));
      }
      expect(statuses, [
        OrderStatus.pending,
        OrderStatus.accepted,
        OrderStatus.completed,
      ]);
      expect(realtime.watches, 1);
      expect(realtime.snapshots.hasListener, isFalse);
    },
  );
}

class FakeRealtime extends RealtimeClient {
  FakeRealtime({this.expectedTopic = 'order.detail'})
    : super(
        baseUrl: 'http://test/api',
        api: ApiClient(
          baseUrl: 'http://test/api',
          sessionStore: MemorySessionStore(),
        ),
      );
  final snapshots = StreamController<Map<String, dynamic>>.broadcast();
  final String expectedTopic;
  int watches = 0;
  @override
  Stream<Map<String, dynamic>> watch(String topic, String orderNo) {
    expect(topic, expectedTopic);
    expect(orderNo, 'ORD-1');
    watches++;
    return snapshots.stream;
  }

  @override
  Future<void> dispose() async {
    await snapshots.close();
  }
}

class MemorySessionStore extends SessionStore {
  @override
  Future<AuthSession?> read() async => const AuthSession(
    accessToken: 'access',
    refreshToken: 'refresh',
    userId: 7,
    userType: 'passenger',
  );
}
