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
    final normalizedKeyword = keyword.trim();
    final normalizedCity = city?.trim();
    final cityResults = await _searchPoi(
      normalizedKeyword,
      city: normalizedCity,
      near: near,
    );
    if (cityResults.isNotEmpty || normalizedCity?.isNotEmpty != true) {
      return cityResults;
    }
    return _searchPoi(normalizedKeyword, near: near);
  }

  /// Searches POIs nationwide with zero-based pagination.
  ///
  /// When [near] is provided, AMap ranks the complete result set by distance
  /// from that GCJ-02 point before applying pagination.
  Future<AmapPoiPage> searchPoiPage(
    String keyword, {
    GeoPoint? near,
    int page = 0,
    int pageSize = 20,
  }) async {
    if (keyword.trim().isEmpty) {
      return AmapPoiPage(
        page: page,
        pageSize: pageSize,
        hasMore: false,
        records: const [],
      );
    }
    if (page < 0) throw ArgumentError.value(page, 'page', '不能小于 0');
    if (pageSize < 1 || pageSize > 50) {
      throw ArgumentError.value(pageSize, 'pageSize', '必须在 1 到 50 之间');
    }
    _check(near);
    final response = await _channel.invokeMethod<Map<Object?, Object?>>(
      'searchPoiPage',
      {
        'keyword': keyword.trim(),
        'page': page,
        'pageSize': pageSize,
        if (near != null) ...{
          'latitude': near.latitude,
          'longitude': near.longitude,
        },
      },
    );
    if (response == null) {
      return AmapPoiPage(
        page: page,
        pageSize: pageSize,
        hasMore: false,
        records: const [],
      );
    }
    final rawRecords = response['records'];
    return AmapPoiPage(
      page: (response['page'] as num?)?.toInt() ?? page,
      pageSize: (response['pageSize'] as num?)?.toInt() ?? pageSize,
      hasMore: response['hasMore'] == true,
      records: rawRecords is List
          ? rawRecords
                .whereType<Map>()
                .map((raw) => _placeFromJson(Map<Object?, Object?>.from(raw)))
                .toList(growable: false)
          : const [],
    );
  }

  Future<List<Place>> _searchPoi(
    String keyword, {
    String? city,
    GeoPoint? near,
  }) async {
    final response = await _channel.invokeMethod<List<dynamic>>('searchPoi', {
      'keyword': keyword,
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
          return _placeFromJson(json);
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
    return Place(
      name: name,
      address: address,
      cityName: json['cityName']?.toString(),
      districtName: json['districtName']?.toString(),
      location: point,
    );
  }

  void _check(GeoPoint? point) {
    if (point != null && point.coordinateSystem != CoordinateSystem.gcj02) {
      throw StateError('高德搜索业务坐标必须使用 GCJ-02。');
    }
  }
}

class AmapPoiPage {
  const AmapPoiPage({
    required this.page,
    required this.pageSize,
    required this.hasMore,
    required this.records,
  });

  final int page;
  final int pageSize;
  final bool hasMore;
  final List<Place> records;
}

Place _placeFromJson(Map<Object?, Object?> json) => Place(
  name: json['name']?.toString() ?? '未命名地点',
  address: json['address']?.toString(),
  amapPoiId: json['poiId']?.toString(),
  cityName: json['cityName']?.toString(),
  districtName: json['districtName']?.toString(),
  distanceM: (json['distanceM'] as num?)?.toInt(),
  location: GeoPoint(
    latitude: (json['latitude'] as num).toDouble(),
    longitude: (json['longitude'] as num).toDouble(),
  ),
);
