import 'package:amap_flutter_base/amap_flutter_base.dart';
import 'package:amap_flutter_map/amap_flutter_map.dart';
import 'package:flutter/material.dart';
import 'geo_point.dart';

/// Shared AMap widget. Callers must provide GCJ-02 points.
class AmapMapView extends StatelessWidget {
  const AmapMapView({
    super.key,
    required this.initialPoint,
    this.markers = const [],
    this.routePoints = const [],
    this.onTap,
  });
  final GeoPoint initialPoint;
  final List<GeoPoint> markers;

  /// Optional GCJ-02 points for a displayed route polyline.
  final List<GeoPoint> routePoints;
  final ValueChanged<GeoPoint>? onTap;

  @override
  Widget build(BuildContext context) {
    _check(initialPoint);
    for (final point in [...markers, ...routePoints]) {
      _check(point);
    }
    return AMapWidget(
      initialCameraPosition: CameraPosition(
        target: _latLng(initialPoint),
        zoom: 15,
      ),
      markers: {
        for (var i = 0; i < markers.length; i++)
          Marker(position: _latLng(markers[i])),
      },
      polylines: routePoints.length < 2
          ? const {}
          : {
              Polyline(
                points: routePoints.map(_latLng).toList(growable: false),
                width: 8,
                color: const Color(0xff1976d2),
                capType: CapType.round,
                joinType: JoinType.round,
              ),
            },
      privacyStatement: const AMapPrivacyStatement(
        hasContains: true,
        hasShow: true,
        hasAgree: true,
      ),
      onTap: onTap == null
          ? null
          : (point) => onTap!(
              GeoPoint(latitude: point.latitude, longitude: point.longitude),
            ),
    );
  }
}

LatLng _latLng(GeoPoint point) {
  _check(point);
  return LatLng(point.latitude, point.longitude);
}

void _check(GeoPoint point) {
  if (point.coordinateSystem != CoordinateSystem.gcj02) {
    throw StateError('高德地图业务坐标必须使用 GCJ-02。');
  }
}
