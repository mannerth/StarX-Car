import 'package:amap_flutter_base/amap_flutter_base.dart';
import 'package:amap_flutter_map/amap_flutter_map.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'geo_point.dart';

/// A colored polyline overlay on [AmapMapView]. Points must be GCJ-02.
class AmapMapRouteLine {
  const AmapMapRouteLine({required this.points, required this.color});

  final List<GeoPoint> points;
  final Color color;
}

/// Shared AMap widget. Callers must provide GCJ-02 points.
class AmapMapView extends StatefulWidget {
  const AmapMapView({
    super.key,
    required this.initialPoint,
    this.markers = const [],
    this.routePoints = const [],
    this.routes = const [],
    this.viewportPoints = const [],
    this.onTap,
  });
  final GeoPoint initialPoint;
  final List<GeoPoint> markers;

  /// Optional GCJ-02 points for the primary (blue) route polyline.
  final List<GeoPoint> routePoints;

  /// Optional colored route polylines drawn on top of [routePoints].
  final List<AmapMapRouteLine> routes;

  /// Points that should remain visible after data updates. A single point is
  /// shown at street level; multiple points are fitted into the viewport.
  final List<GeoPoint> viewportPoints;
  final ValueChanged<GeoPoint>? onTap;

  @override
  State<AmapMapView> createState() => _AmapMapViewState();
}

class _AmapMapViewState extends State<AmapMapView> {
  AMapController? _controller;

  @override
  void didUpdateWidget(covariant AmapMapView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_samePoints(_cameraPoints(oldWidget), _cameraPoints(widget)) ||
        (_cameraPoints(widget).isEmpty &&
            oldWidget.initialPoint != widget.initialPoint)) {
      _moveCamera();
    }
  }

  @override
  Widget build(BuildContext context) {
    _check(widget.initialPoint);
    for (final point in [
      ...widget.markers,
      ...widget.routePoints,
      ...widget.viewportPoints,
    ]) {
      _check(point);
    }
    for (final route in widget.routes) {
      for (final point in route.points) {
        _check(point);
      }
    }
    return AMapWidget(
      initialCameraPosition: CameraPosition(
        target: _latLng(widget.initialPoint),
        zoom: 15,
      ),
      markers: {
        for (var i = 0; i < widget.markers.length; i++)
          Marker(
            position: _latLng(widget.markers[i]),
            icon: BitmapDescriptor.defaultMarkerWithHue(
              i == 0 ? BitmapDescriptor.hueGreen : BitmapDescriptor.hueRed,
            ),
          ),
      },
      polylines: {
        if (widget.routePoints.length >= 2)
          Polyline(
            points: widget.routePoints.map(_latLng).toList(growable: false),
            width: 8,
            color: const Color(0xff1976d2),
            capType: CapType.round,
            joinType: JoinType.round,
          ),
        for (final route in widget.routes)
          if (route.points.length >= 2)
            Polyline(
              points: route.points.map(_latLng).toList(growable: false),
              width: 8,
              color: route.color,
              capType: CapType.round,
              joinType: JoinType.round,
            ),
      },
      privacyStatement: const AMapPrivacyStatement(
        hasContains: true,
        hasShow: true,
        hasAgree: true,
      ),
      // 注册 EagerGestureRecognizer，让地图参与手势竞技场。
      // 平台视图默认不参与竞技场，放入 ListView/滚动容器时所有指针
      // 事件都会被父级滚动识别器抢走，导致地图无法拖动和缩放。
      gestureRecognizers: {
        Factory<EagerGestureRecognizer>(EagerGestureRecognizer.new),
      },
      onMapCreated: (controller) {
        _controller = controller;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _moveCamera();
        });
      },
      onTap: widget.onTap == null
          ? null
          : (point) => widget.onTap!(
              GeoPoint(latitude: point.latitude, longitude: point.longitude),
            ),
    );
  }

  void _moveCamera() {
    final controller = _controller;
    if (controller == null) return;
    final points = _cameraPoints(widget);
    if (points.length < 2) {
      final point = points.isEmpty ? widget.initialPoint : points.single;
      controller.moveCamera(CameraUpdate.newLatLngZoom(_latLng(point), 15));
      return;
    }
    var minLatitude = points.first.latitude;
    var maxLatitude = points.first.latitude;
    var minLongitude = points.first.longitude;
    var maxLongitude = points.first.longitude;
    for (final point in points.skip(1)) {
      minLatitude = point.latitude < minLatitude ? point.latitude : minLatitude;
      maxLatitude = point.latitude > maxLatitude ? point.latitude : maxLatitude;
      minLongitude = point.longitude < minLongitude
          ? point.longitude
          : minLongitude;
      maxLongitude = point.longitude > maxLongitude
          ? point.longitude
          : maxLongitude;
    }
    if (minLatitude == maxLatitude && minLongitude == maxLongitude) {
      controller.moveCamera(
        CameraUpdate.newLatLngZoom(_latLng(points.first), 15),
      );
      return;
    }
    controller.moveCamera(
      CameraUpdate.newLatLngBounds(
        LatLngBounds(
          southwest: LatLng(minLatitude, minLongitude),
          northeast: LatLng(maxLatitude, maxLongitude),
        ),
        72,
      ),
      duration: 400,
    );
  }
}

List<GeoPoint> _cameraPoints(AmapMapView map) {
  if (map.viewportPoints.isNotEmpty) return map.viewportPoints;
  final routePoints = <GeoPoint>[
    ...map.routePoints,
    for (final route in map.routes) ...route.points,
  ];
  if (routePoints.isNotEmpty) return routePoints;
  return map.markers;
}

bool _samePoints(List<GeoPoint> left, List<GeoPoint> right) {
  if (left.length != right.length) return false;
  for (var i = 0; i < left.length; i++) {
    if (left[i].latitude != right[i].latitude ||
        left[i].longitude != right[i].longitude ||
        left[i].coordinateSystem != right[i].coordinateSystem) {
      return false;
    }
  }
  return true;
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
