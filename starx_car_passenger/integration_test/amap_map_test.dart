import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:starx_car_common/starx_car_common.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('AMap widget mounts with a GCJ-02 point', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AmapMapView(
            initialPoint: GeoPoint(latitude: 31.2304, longitude: 121.4737),
          ),
        ),
      ),
    );
    await tester.pump(const Duration(seconds: 3));

    expect(find.byType(AmapMapView), findsOneWidget);
  });
}
