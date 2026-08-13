import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Result of a location permission request on Android.
enum LocationPermissionStatus {
  /// The app may access the device location.
  granted,

  /// The user denied the request but can be asked again later.
  denied,

  /// The user chose "don't ask again" (or the OS policy stops prompting);
  /// the app must guide them to system settings.
  permanentlyDenied,
}

/// Requests Android runtime location permission through the shared plugin.
///
/// This is the role-neutral entry point so both apps can request the
/// foreground location permission without duplicating platform channels.
/// Non-Android targets are treated as already granted because this project
/// only ships Android apps.
class AmapPermissionService {
  const AmapPermissionService();

  static const MethodChannel _channel = MethodChannel(
    'starx_car_common/location_permission',
  );

  Future<bool> isLocationGranted() async {
    if (defaultTargetPlatform != TargetPlatform.android) return true;
    final result = await _channel.invokeMethod<bool>('isLocationGranted');
    return result ?? false;
  }

  Future<LocationPermissionStatus> requestLocation() async {
    if (defaultTargetPlatform != TargetPlatform.android) {
      return LocationPermissionStatus.granted;
    }
    final result = await _channel.invokeMethod<String>('requestLocation');
    return switch (result) {
      'granted' => LocationPermissionStatus.granted,
      'permanentlyDenied' => LocationPermissionStatus.permanentlyDenied,
      _ => LocationPermissionStatus.denied,
    };
  }
}
