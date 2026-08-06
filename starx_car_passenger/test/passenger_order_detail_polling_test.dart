import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';
import 'package:starx_car_passenger/core/providers.dart';
import 'package:starx_car_passenger/features/orders/passenger_order_repository.dart';
import 'package:starx_car_passenger/features/orders/passenger_orders_page.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'order detail polling follows status changes and stops at completion',
    () async {
      final repository = SequenceOrderRepository([
        _detail(OrderStatus.pending),
        _detail(OrderStatus.accepted),
        _detail(OrderStatus.completed),
      ]);
      final container = ProviderContainer(
        overrides: [
          sessionStoreProvider.overrideWithValue(
            MemorySessionStore(
              const AuthSession(
                accessToken: 'access',
                refreshToken: 'refresh',
                userId: 7,
                userType: 'passenger',
              ),
            ),
          ),
          passengerOrderRepositoryProvider.overrideWithValue(repository),
          passengerOrderDetailPollIntervalProvider.overrideWithValue(
            const Duration(milliseconds: 100),
          ),
        ],
      );
      addTearDown(container.dispose);

      final provider = passengerOrderDetailProvider('ORD-1');
      final statuses = <OrderStatus>[];
      final completed = Completer<void>();
      final subscription = container.listen<AsyncValue<OrderDetail>>(provider, (
        _,
        next,
      ) {
        next.whenData((detail) {
          statuses.add(detail.status);
          if (detail.status == OrderStatus.completed &&
              !completed.isCompleted) {
            completed.complete();
          }
        });
      }, fireImmediately: true);
      addTearDown(subscription.close);

      await completed.future.timeout(const Duration(seconds: 2));
      expect(statuses, [OrderStatus.accepted, OrderStatus.completed]);
      expect(repository.returnedStatuses, [
        OrderStatus.pending,
        OrderStatus.accepted,
        OrderStatus.completed,
      ]);
      expect(repository.calls, 3);
    },
  );
}

OrderDetail _detail(OrderStatus status) => OrderDetail(
  orderNo: 'ORD-1',
  status: status,
  pickup: const Place(
    name: '上车点',
    location: GeoPoint(latitude: 31.2, longitude: 121.4),
  ),
  destination: const Place(
    name: '目的地',
    location: GeoPoint(latitude: 31.21, longitude: 121.42),
  ),
  estimatedDistanceM: 4200,
  estimatedDurationS: 900,
  billingDistanceM: status == OrderStatus.completed ? 4200 : 0,
  fare: const FareQuote(
    baseFare: '5.00',
    distanceFare: '12.00',
    drivingTimeFare: '1.00',
    waitingFare: '0.50',
    totalFare: '18.50',
  ),
  estimatedFare: status != OrderStatus.completed,
);

class SequenceOrderRepository extends PassengerOrderRepository {
  SequenceOrderRepository(this._details)
    : super(ApiClient(baseUrl: 'http://test', sessionStore: SessionStore()));

  final List<OrderDetail> _details;
  int calls = 0;
  final returnedStatuses = <OrderStatus>[];

  @override
  Future<OrderDetail> detail(String orderNo, {CancelToken? cancelToken}) async {
    calls++;
    await Future<void>.delayed(const Duration(milliseconds: 20));
    final detail = _details.removeAt(0);
    returnedStatuses.add(detail.status);
    return detail;
  }
}

class MemorySessionStore extends SessionStore {
  MemorySessionStore(this.value) : super();

  final AuthSession value;

  @override
  Future<AuthSession?> read() async => value;
}
