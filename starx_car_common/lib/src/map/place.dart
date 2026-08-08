import 'geo_point.dart';

/// A searchable location or point of interest selected on the map.
class Place {
  const Place({
    required this.name,
    required this.location,
    this.address,
    this.amapPoiId,
    this.cityName,
    this.districtName,
    this.distanceM,
  });

  /// Display name returned by AMap, for example a building or station name.
  final String name;

  /// Map coordinate of this place.
  final GeoPoint location;

  /// Human-readable address, when available.
  final String? address;

  /// AMap POI identifier, when the place originates from a POI search.
  final String? amapPoiId;

  /// City and district labels used to disambiguate similarly named POIs.
  final String? cityName;
  final String? districtName;

  /// Distance from the search center, when AMap provides one.
  final int? distanceM;

  /// Converts the selected GCJ-02 place to the backend request shape.
  Map<String, dynamic> toJson() {
    if (location.coordinateSystem != CoordinateSystem.gcj02) {
      throw StateError('只有 GCJ-02 坐标可以提交给业务接口。');
    }
    return {
      if (amapPoiId != null && amapPoiId!.isNotEmpty) 'poiId': amapPoiId,
      'name': name,
      if (address != null && address!.isNotEmpty) 'address': address,
      'latitude': _apiCoordinate(location.latitude),
      'longitude': _apiCoordinate(location.longitude),
    };
  }
}

double _apiCoordinate(double value) => double.parse(value.toStringAsFixed(6));
