import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';
import 'package:starx_car_driver/core/providers.dart';
import 'package:starx_car_driver/features/auth/auth_controller.dart';
import 'package:starx_car_driver/features/home/driver_repository.dart';
import 'package:starx_car_driver/features/work/driver_work_controller.dart';
import 'package:starx_car_driver/features/work/order_realtime.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'event bursts preserve the in-flight read and schedule one trailing refresh',
    () async {
      final changes = StreamController<int>();
      final gate = Completer<OrderPage>();
      final repository = PendingRepository()..nextResponse = gate;
      final container = ProviderContainer(
        overrides: [
          sessionStoreProvider.overrideWithValue(DriverSessionStore()),
          driverRepositoryProvider.overrideWithValue(repository),
          driverWorkProvider.overrideWith(FakeWork.new),
          driverOrderChangesProvider.overrideWith((ref) => changes.stream),
        ],
      );
      addTearDown(() async {
        container.dispose();
        await changes.close();
      });
      await container.read(driverAuthProvider.future);
      await container.read(driverWorkProvider.future);
      final subscription = container.listen(pendingOrdersProvider, (_, _) {});
      addTearDown(subscription.close);
      final first = container.read(pendingOrdersProvider.future);
      changes.add(1);
      changes.add(2);
      changes.add(3);
      await container.pump();
      expect(repository.calls, 1);
      expect(repository.lastCancelToken?.isCancelled, isFalse);
      gate.complete(const OrderPage(page: 1, size: 20, total: 0, records: []));
      await first;
      await Future<void>.delayed(Duration.zero);
      await container.pump();
      await container.read(pendingOrdersProvider.future);
      expect(repository.calls, 2);
    },
  );
  test(
    'pushed changes refresh pending orders and offline status stops querying',
    () async {
      final changes = StreamController<int>();
      final repository = PendingRepository();
      final container = ProviderContainer(
        overrides: [
          sessionStoreProvider.overrideWithValue(DriverSessionStore()),
          driverRepositoryProvider.overrideWithValue(repository),
          driverWorkProvider.overrideWith(FakeWork.new),
          driverOrderChangesProvider.overrideWith((ref) => changes.stream),
        ],
      );
      addTearDown(() async {
        container.dispose();
        await changes.close();
      });
      await container.read(driverAuthProvider.future);
      await container.read(driverWorkProvider.future);
      final subscription = container.listen(pendingOrdersProvider, (_, _) {});
      addTearDown(subscription.close);
      await container.read(pendingOrdersProvider.future);
      expect(repository.calls, 1);
      changes.add(1);
      await container.pump();
      await container.read(pendingOrdersProvider.future);
      expect(repository.calls, 2);
      (container.read(driverWorkProvider.notifier) as FakeWork).offline();
      await container.pump();
      expect((await container.read(pendingOrdersProvider.future)).total, 0);
      expect(repository.calls, 2);
    },
  );
}

class FakeWork extends DriverWorkController {
  @override
  Future<bool> build() async => true;
  void offline() {
    state = const AsyncData(false);
  }
}

class PendingRepository extends DriverRepository {
  PendingRepository()
    : super(
        ApiClient(
          baseUrl: 'http://test/api',
          sessionStore: DriverSessionStore(),
        ),
      );
  int calls = 0;
  Completer<OrderPage>? nextResponse;
  CancelToken? lastCancelToken;
  @override
  Future<OrderPage> pendingOrders({
    int page = 1,
    int size = 20,
    CancelToken? cancelToken,
  }) async {
    calls++;
    lastCancelToken = cancelToken;
    final response = nextResponse;
    nextResponse = null;
    if (response != null) return response.future;
    return const OrderPage(page: 1, size: 20, total: 0, records: []);
  }
}

class DriverSessionStore extends SessionStore {
  @override
  Future<AuthSession?> read() async => const AuthSession(
    accessToken: 'access',
    refreshToken: 'refresh',
    userId: 99,
    userType: 'driver',
  );
}
