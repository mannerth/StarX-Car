/// Coordinate systems that may be encountered by the mobile applications.
enum CoordinateSystem {
  /// The coordinate system used by AMap/高德地图 in mainland China.
  gcj02,

  /// GPS coordinates used by the underlying device location service.
  wgs84,
}

/// An immutable geographic point.
///
/// AMap-facing business values should use [CoordinateSystem.gcj02]. Keeping
/// the coordinate system explicit prevents accidental mixing of GPS and AMap
/// coordinates, which would visibly shift markers on the map.
class GeoPoint {
  const GeoPoint({
    required this.latitude,
    required this.longitude,
    this.coordinateSystem = CoordinateSystem.gcj02,
  }) : assert(latitude >= -90 && latitude <= 90),
       assert(longitude >= -180 && longitude <= 180);

  final double latitude;
  final double longitude;
  final CoordinateSystem coordinateSystem;
}
