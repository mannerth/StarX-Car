import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';

void main() {
  final now = DateTime(2026, 10, 18, 12);
  AmapLocationUpdate fix({
    double accuracy = 8,
    DateTime? time,
    double latitude = 31.2,
  }) => AmapLocationUpdate(
    point: GeoPoint(latitude: latitude, longitude: 121.4),
    accuracyM: accuracy,
    recordedTime: time ?? now,
  );
  test('accepts a fresh precise GCJ-02 fix', () {
    expect(fix().isUsable(now: now), isTrue);
    expect(fix(accuracy: 100).isUsable(now: now), isTrue);
  });
  test('rejects coarse, invalid, stale and future fixes', () {
    expect(fix(accuracy: 1000).isUsable(now: now), isFalse);
    expect(fix(accuracy: double.nan).isUsable(now: now), isFalse);
    expect(
      AmapLocationUpdate.fromRaw({'latitude': 91, 'longitude': 121.4}).point,
      isNull,
    );
    expect(
      fix(time: now.subtract(const Duration(seconds: 16))).isUsable(now: now),
      isFalse,
    );
    expect(
      fix(time: now.add(const Duration(seconds: 6))).isUsable(now: now),
      isFalse,
    );
    expect(const AmapLocationUpdate().isUsable(now: now), isFalse);
  });
}
