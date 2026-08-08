import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';

void main() {
  test('Place serializes AMap coordinates at backend precision', () {
    const place = Place(
      name: '测试地点',
      cityName: '北京市',
      districtName: '东城区',
      distanceM: 1200,
      location: GeoPoint(latitude: 39.123456789, longitude: 116.987654321),
    );

    expect(place.toJson(), containsPair('latitude', 39.123457));
    expect(place.toJson(), containsPair('longitude', 116.987654));
    expect(place.toJson(), isNot(contains('cityName')));
    expect(place.toJson(), isNot(contains('distanceM')));
  });

  test('Place rejects non-GCJ-02 coordinates', () {
    const place = Place(
      name: 'GPS 地点',
      location: GeoPoint(
        latitude: 39,
        longitude: 116,
        coordinateSystem: CoordinateSystem.wgs84,
      ),
    );

    expect(place.toJson, throwsStateError);
  });
}
