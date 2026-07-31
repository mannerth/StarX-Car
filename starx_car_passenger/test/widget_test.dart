import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_passenger/app.dart';

void main() {
  test('passenger application root can be created', () {
    expect(const PassengerApp(), isA<PassengerApp>());
  });
}
