import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';
import '../auth/auth_controller.dart';
import '../home/driver_repository.dart';

final driverWorkProvider = AsyncNotifierProvider<DriverWorkController, bool>(
  DriverWorkController.new,
);

final driverLocationErrorProvider =
    NotifierProvider<DriverLocationErrorController, String?>(
      DriverLocationErrorController.new,
    );

class DriverLocationErrorController extends Notifier<String?> {
  @override
  String? build() => null;
}

class DriverWorkController extends AsyncNotifier<bool> {
  static const _foregroundChannel = MethodChannel(
    'starx_car_driver/foreground',
  );
  AmapLocationService? _location;
  StreamSubscription<AmapLocationUpdate>? _locationSubscription;
  Timer? _pendingTimer;
  Timer? _locationRetryTimer;
  AmapLocationUpdate? _pendingLocation;
  bool _sendingLocation = false;
  int _locationFailureCount = 0;
  bool _online = false;
  DriverRepository? _activeRepository;
  CancelToken? _locationCancelToken;
  AppLifecycleListener? _lifecycleListener;
  bool _foregroundServiceStarted = false;

  @override
  Future<bool> build() async {
    ref.watch(driverAuthProvider);
    ref.onDispose(_disposeTasks);
    final driver = await ref.read(driverRepositoryProvider).getDriver();
    final online =
        driver['workStatus'] == 'online' || driver['workStatus'] == 'busy';
    _online = online;
    _ensureLifecycleListener();
    if (online && _isForeground) {
      await _startForegroundService();
      _startTasks(ref.read(driverRepositoryProvider));
    }
    return online;
  }

  Future<void> setOnline(bool online) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final repo = ref.read(driverRepositoryProvider);
      if (online) {
        await _startForegroundService();
        try {
          await repo.updateWorkStatus('online');
        } on Object {
          await _stopForegroundService();
          rethrow;
        }
        _online = true;
        _activeRepository = repo;
        _startTasks(repo);
        ref.invalidate(pendingOrdersProvider);
      } else {
        await repo.updateWorkStatus('offline');
        _online = false;
        _pauseTasks();
        await _stopForegroundService();
        ref.invalidate(pendingOrdersProvider);
      }
      return online;
    });
  }

  void _startTasks(DriverRepository repo) {
    if (!_online || !_isForeground) return;
    _activeRepository = repo;
    _ensureLifecycleListener();
    _location ??= AmapLocationService();
    final cancelToken = _locationCancelToken ??= CancelToken();
    _setLocationError(null);
    _locationSubscription ??= _location!.updates.listen((update) {
      if (update.point == null) {
        _setLocationError(update.errorMessage ?? '无法获取当前位置，请检查定位权限和 GPS。');
        return;
      }
      _pendingLocation = update;
      unawaited(_drainLocationQueue(repo, cancelToken));
    });
    _location!.start();
    _pendingTimer ??= Timer.periodic(
      const Duration(seconds: 5),
      (_) => ref.invalidate(pendingOrdersProvider),
    );
  }

  void _disposeTasks() {
    _online = false;
    _pauseTasks();
    _lifecycleListener?.dispose();
    _lifecycleListener = null;
    unawaited(_stopForegroundService());
  }

  void _pauseTasks() {
    _pendingTimer?.cancel();
    _pendingTimer = null;
    _locationRetryTimer?.cancel();
    _locationRetryTimer = null;
    _pendingLocation = null;
    _locationFailureCount = 0;
    _locationSubscription?.cancel();
    _locationSubscription = null;
    final location = _location;
    _location = null;
    final cancelToken = _locationCancelToken;
    _locationCancelToken = null;
    cancelToken?.cancel('driver work tasks paused');
    unawaited(location?.dispose());
  }

  void _ensureLifecycleListener() {
    _lifecycleListener ??= AppLifecycleListener(
      onStateChange: (state) {
        if (state == AppLifecycleState.resumed) {
          final repo = _activeRepository;
          if (_online && repo != null) _startTasks(repo);
        } else if (state == AppLifecycleState.inactive ||
            state == AppLifecycleState.hidden ||
            state == AppLifecycleState.paused) {
          if (!_foregroundServiceStarted) _pauseTasks();
        }
      },
    );
  }

  bool get _isForeground =>
      WidgetsBinding.instance.lifecycleState == null ||
      WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;

  Future<void> _startForegroundService() async {
    if (defaultTargetPlatform != TargetPlatform.android ||
        _foregroundServiceStarted) {
      return;
    }
    try {
      await _foregroundChannel.invokeMethod<void>('start');
    } on PlatformException catch (error) {
      throw ApiException(error.message ?? '无法启动司机前台定位服务。');
    }
    _foregroundServiceStarted = true;
  }

  Future<void> _stopForegroundService() async {
    if (defaultTargetPlatform != TargetPlatform.android ||
        !_foregroundServiceStarted) {
      return;
    }
    try {
      await _foregroundChannel.invokeMethod<void>('stop');
    } finally {
      _foregroundServiceStarted = false;
    }
  }

  Future<void> _drainLocationQueue(
    DriverRepository repo,
    CancelToken cancelToken,
  ) async {
    if (_sendingLocation) return;
    final update = _pendingLocation;
    if (update?.point == null) return;
    _pendingLocation = null;
    _sendingLocation = true;
    try {
      await repo
          .reportLocation(
            point: update!.point!,
            recordedTime: update.recordedTime ?? DateTime.now(),
            accuracyM: update.accuracyM,
            speedMps: update.speedMps,
            bearing: update.bearing,
            cancelToken: cancelToken,
          )
          .timeout(const Duration(seconds: 8));
      _locationFailureCount = 0;
      _locationRetryTimer?.cancel();
      _locationRetryTimer = null;
      _setLocationError(null);
    } on Object catch (error) {
      if (error is ApiException && error.cancelled) return;
      // Keep the newest callback if one arrived while this request was in
      // flight; otherwise retry the failed point once connectivity returns.
      _pendingLocation ??= update;
      _locationFailureCount++;
      _setLocationError(
        error is ApiException ? error.message : '位置上报失败，将自动重试。',
      );
      _scheduleLocationRetry(repo, cancelToken);
    } finally {
      _sendingLocation = false;
      if (identical(_locationCancelToken, cancelToken) &&
          _pendingLocation != null &&
          _locationRetryTimer == null) {
        unawaited(_drainLocationQueue(repo, cancelToken));
      }
    }
  }

  void _scheduleLocationRetry(DriverRepository repo, CancelToken cancelToken) {
    if (_locationRetryTimer != null) return;
    final seconds = (1 << (_locationFailureCount.clamp(0, 5))).clamp(1, 30);
    _locationRetryTimer = Timer(Duration(seconds: seconds), () {
      if (!identical(_locationCancelToken, cancelToken)) return;
      _locationRetryTimer = null;
      unawaited(_drainLocationQueue(repo, cancelToken));
    });
  }

  void _setLocationError(String? message) {
    ref.read(driverLocationErrorProvider.notifier).state = message;
  }
}

final pendingOrdersProvider = FutureProvider.autoDispose<OrderPage>((ref) {
  ref.watch(driverAuthProvider);
  // Do not watch driverWorkProvider here: driverWorkProvider owns the timer
  // that invalidates this provider, and watching it creates a Riverpod cycle
  // while both providers are being initialized. Work-status changes explicitly
  // invalidate pendingOrdersProvider after the mutation completes.
  final online = ref.read(driverWorkProvider).value ?? false;
  if (!online) {
    return const OrderPage(page: 1, size: 20, total: 0, records: []);
  }
  final cancelToken = CancelToken();
  ref.onDispose(() => cancelToken.cancel('pending orders provider disposed'));
  return ref
      .read(driverRepositoryProvider)
      .pendingOrders(cancelToken: cancelToken);
});

final driverOrdersProvider = FutureProvider.autoDispose<OrderPage>((ref) {
  ref.watch(driverAuthProvider);
  final cancelToken = CancelToken();
  ref.onDispose(() => cancelToken.cancel('driver orders provider disposed'));
  return ref.read(driverRepositoryProvider).orders(cancelToken: cancelToken);
});
