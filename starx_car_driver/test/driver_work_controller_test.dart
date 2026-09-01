import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';
import 'package:starx_car_driver/features/work/driver_work_controller.dart';

void main() {
  test('order polling does not replace an in-flight request', () {
    expect(shouldStartOrderRefresh(const AsyncLoading<OrderPage>()), isFalse);
    expect(
      shouldStartOrderRefresh(
        const AsyncData(OrderPage(page: 1, size: 20, total: 0, records: [])),
      ),
      isTrue,
    );
    expect(
      shouldStartOrderRefresh(
        AsyncError<OrderPage>(StateError('request failed'), StackTrace.empty),
      ),
      isTrue,
    );
  });
}
