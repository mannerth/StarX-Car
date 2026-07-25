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
}
