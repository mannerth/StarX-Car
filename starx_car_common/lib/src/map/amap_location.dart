import 'dart:async';

import 'package:amap_flutter_location/amap_flutter_location.dart';
import 'package:amap_flutter_location/amap_location_option.dart';
import 'geo_point.dart';

/// A single raw location callback, including failures that have no point.
class AmapLocationUpdate {
  const AmapLocationUpdate({
    this.point,
    this.errorCode,
    this.errorMessage,
    this.accuracyM,
    this.speedMps,
    this.bearing,
    this.recordedTime,
  });

  final GeoPoint? point;
  final int? errorCode;
  final String? errorMessage;
  final double? accuracyM;
  final double? speedMps;
  final double? bearing;
  final DateTime? recordedTime;

  bool get hasError => point == null;

  factory AmapLocationUpdate.fromRaw(Map<String, Object> raw) {
    final errorCode = int.tryParse(raw['errorCode']?.toString() ?? '0');
    final latitude = double.tryParse(raw['latitude']?.toString() ?? '');
    final longitude = double.tryParse(raw['longitude']?.toString() ?? '');
    final point = errorCode == null || errorCode == 0
        ? latitude == null || longitude == null
              ? null
              : GeoPoint(latitude: latitude, longitude: longitude)
        : null;
    return AmapLocationUpdate(
      point: point,
      errorCode: errorCode,
      errorMessage: raw['errorInfo']?.toString(),
      accuracyM: _number(raw['accuracy']),
      speedMps: _number(raw['speed']),
      bearing: _number(raw['bearing']),
      recordedTime: DateTime.tryParse(raw['locationTime']?.toString() ?? ''),
    );
  }
}

/// Location source shared by both apps; each app controls its own lifecycle.
class AmapLocationService {
  AmapLocationService() : _plugin = AMapFlutterLocation() {
    // The SDK will refuse to emit locations until this declaration is made.
    AMapFlutterLocation.updatePrivacyShow(true, true);
    AMapFlutterLocation.updatePrivacyAgree(true);
  }

  final AMapFlutterLocation _plugin;

  late final Stream<AmapLocationUpdate> updates = _plugin
      .onLocationChanged()
      .map(AmapLocationUpdate.fromRaw)
      .asBroadcastStream();

  Stream<GeoPoint> get positions => updates
      .where((update) => update.point != null)
      .map((update) => update.point!);

  /// Requests one location update and stops the native location client.
  ///
  /// The caller owns the service and should still call [dispose] when the
  /// surrounding page or controller is destroyed. A failed native callback
  /// is returned as an [AmapLocationUpdate] so role apps can present the
  /// provider's permission/GPS message without duplicating plugin details.
  Future<AmapLocationUpdate> locateOnce({
    Duration timeout = const Duration(seconds: 15),
  }) async {
    final firstUpdate = updates.first;
    _plugin.setLocationOption(
      AMapLocationOption()
        ..onceLocation = true
        ..locationInterval = 1000
        ..needAddress = false,
    );
    _plugin.startLocation();
    try {
      return await firstUpdate.timeout(timeout);
    } finally {
      _plugin.stopLocation();
    }
  }

  void start({Duration interval = const Duration(seconds: 3)}) {
    _plugin.setLocationOption(
      AMapLocationOption()
        ..onceLocation = false
        ..locationInterval = interval.inMilliseconds
        ..needAddress = false,
    );
    _plugin.startLocation();
  }

  void stop() => _plugin.stopLocation();
  Future<void> dispose() async {
    _plugin.stopLocation();
    _plugin.destroy();
  }
}

double? _number(Object? value) =>
    value == null ? null : double.tryParse(value.toString());
