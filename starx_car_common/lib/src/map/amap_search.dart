import 'package:flutter/services.dart';

import 'geo_point.dart';
import 'place.dart';

/// POI and reverse-geocoding access backed by the official AMap Android SDK.
class AmapSearchService {
  const AmapSearchService();

  static const MethodChannel _channel = MethodChannel(
    'starx_car_common/amap_route',
  );

  Future<List<Place>> searchPoi(
    String keyword, {
    String? city,
    GeoPoint? near,
  }) async {
    if (keyword.trim().isEmpty) return const [];
    _check(near);
    final response = await _channel.invokeMethod<List<dynamic>>('searchPoi', {
      'keyword': keyword.trim(),
      if (city != null && city.trim().isNotEmpty) 'city': city.trim(),
      if (near != null) ...{
        'latitude': near.latitude,
        'longitude': near.longitude,
      },
    });
    if (response == null) return const [];
    return response
        .whereType<Map>()
        .map((raw) {
          final json = Map<Object?, Object?>.from(raw);
          return Place(
            name: json['name']?.toString() ?? '未命名地点',
            address: json['address']?.toString(),
            amapPoiId: json['poiId']?.toString(),
            location: GeoPoint(
              latitude: (json['latitude'] as num).toDouble(),
              longitude: (json['longitude'] as num).toDouble(),
            ),
          );
        })
        .toList(growable: false);
  }

  Future<Place?> reverseGeocode(GeoPoint point) async {
    _check(point);
    final json = await _channel.invokeMethod<Map<Object?, Object?>>(
      'reverseGeocode',
      {'latitude': point.latitude, 'longitude': point.longitude},
    );
    if (json == null) return null;
    final name = json['name']?.toString();
    final address = json['address']?.toString();
    if (name == null || name.isEmpty) return null;
    return Place(name: name, address: address, location: point);
  }

  void _check(GeoPoint? point) {
    if (point != null && point.coordinateSystem != CoordinateSystem.gcj02) {
      throw StateError('高德搜索业务坐标必须使用 GCJ-02。');
    }
  }
}
