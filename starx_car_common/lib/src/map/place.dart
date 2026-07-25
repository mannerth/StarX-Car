import 'geo_point.dart';

/// A searchable location or point of interest selected on the map.
class Place {
  const Place({
    required this.name,
    required this.location,
    this.address,
    this.amapPoiId,
  });

  /// Display name returned by AMap, for example a building or station name.
  final String name;

  /// Map coordinate of this place.
  final GeoPoint location;

  /// Human-readable address, when available.
  final String? address;

  /// AMap POI identifier, when the place originates from a POI search.
  final String? amapPoiId;
}
