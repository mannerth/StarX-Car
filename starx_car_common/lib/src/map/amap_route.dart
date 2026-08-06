import 'package:flutter/services.dart';

import 'geo_point.dart';

/// A native AMap Android driving route in GCJ-02 coordinates.
class AmapDrivingRoute {
  const AmapDrivingRoute({
    required this.distanceM,
    required this.durationS,
    required this.points,
  });

  final int distanceM;
  final int durationS;
  final List<GeoPoint> points;

  factory AmapDrivingRoute.fromJson(Map<Object?, Object?> json) {
    final rawPoints = json['points'];
    final points = rawPoints is List
        ? rawPoints
              .whereType<List>()
              .where((point) => point.length >= 2)
              .map(
                (point) => GeoPoint(
                  latitude: (point[0] as num).toDouble(),
                  longitude: (point[1] as num).toDouble(),
                ),
              )
              .toList(growable: false)
        : const <GeoPoint>[];
    return AmapDrivingRoute(
      distanceM: (json['distanceM'] as num).toInt(),
      durationS: (json['durationS'] as num).toInt(),
      points: points,
    );
  }
}

/// Uses the official AMap Android route-search SDK, so Android map keys are
/// sufficient and a Web Service key is not required.
class AmapRouteService {
  const AmapRouteService();

  static const MethodChannel _channel = MethodChannel(
    'starx_car_common/amap_route',
  );

  Future<AmapDrivingRoute> planDriving({
    required GeoPoint origin,
    required GeoPoint destination,
  }) async {
    _check(origin);
    _check(destination);
    final response = await _channel
        .invokeMethod<Map<Object?, Object?>>('planDriving', {
          'originLat': origin.latitude,
          'originLng': origin.longitude,
          'destinationLat': destination.latitude,
          'destinationLng': destination.longitude,
        });
    if (response == null) throw StateError('高德路线服务没有返回结果。');
    return AmapDrivingRoute.fromJson(response);
  }

  void _check(GeoPoint point) {
    if (point.coordinateSystem != CoordinateSystem.gcj02) {
      throw StateError('高德路线业务坐标必须使用 GCJ-02。');
    }
  }
}
