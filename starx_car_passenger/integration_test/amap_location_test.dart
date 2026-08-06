import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:starx_car_common/starx_car_common.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('AMap one-shot location returns a result', (tester) async {
    final service = AmapLocationService();
    addTearDown(service.dispose);

    final update = await service.locateOnce(timeout: const Duration(seconds: 10));
    // Keep only non-sensitive status metadata in test output.
    debugPrint(
      'AMap location result: hasPoint=${update.point != null}, '
      'errorCode=${update.errorCode}',
    );

    // A simulator may have no GPS fix, but the native bridge must return a
    // structured success/error callback rather than hang or throw.
    expect(update, isA<AmapLocationUpdate>());
  });
}
