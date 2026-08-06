import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:starx_car_driver/main.dart' as app;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('driver foreground service starts and stops with permissions', (
    tester,
  ) async {
    app.main();
    await tester.pump(const Duration(seconds: 2));

    const channel = MethodChannel('starx_car_driver/foreground');
    await channel.invokeMethod<void>('start');
    await tester.pump(const Duration(seconds: 1));
    await channel.invokeMethod<void>('stop');
  });
}
