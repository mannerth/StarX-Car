import 'package:url_launcher/url_launcher.dart';

import 'geo_point.dart';

/// Opens the installed AMap Android application for a driving route.
///
/// This is intentionally an external navigation hand-off. The apps keep
/// order state and location reporting in their own modules; common only
/// validates the coordinate contract and constructs the AMap URI.
class AmapNavigationService {
  const AmapNavigationService();

  Future<bool> openDrivingRoute({
    GeoPoint? start,
    required GeoPoint destination,
    String? destinationName,
  }) async {
    _check(start);
    _check(destination);
    final parameters = <String, String>{
      'sourceApplication': 'starx_car',
      'dlat': destination.latitude.toString(),
      'dlon': destination.longitude.toString(),
      'dev': '0',
      't': '0',
    };
    if (destinationName != null && destinationName.trim().isNotEmpty) {
      parameters['dname'] = destinationName.trim();
    }
    if (start != null) {
      parameters['slat'] = start.latitude.toString();
      parameters['slon'] = start.longitude.toString();
    }
    final uri = Uri(
      scheme: 'amapuri',
      host: 'route',
      path: '/plan/',
      queryParameters: parameters,
    );
    return launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  void _check(GeoPoint? point) {
    if (point != null && point.coordinateSystem != CoordinateSystem.gcj02) {
      throw StateError('高德导航业务坐标必须使用 GCJ-02。');
    }
  }
}
