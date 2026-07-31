import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_driver/app.dart';

void main() {
  test('driver application root can be created', () {
    expect(const DriverApp(), isA<DriverApp>());
  });
}
