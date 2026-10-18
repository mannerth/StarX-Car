import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';
import '../auth/auth_controller.dart';
import '../home/driver_repository.dart';
import 'order_realtime.dart';

final driverWorkProvider = AsyncNotifierProvider<DriverWorkController, bool>(
  DriverWorkController.new,
);

class DriverWorkController extends AsyncNotifier<bool> {
  static const _foregroundChannel = MethodChannel(
    'starx_car_driver/foreground',
  );
  AmapLocationService? _location;
  StreamSubscription<AmapLocationUpdate>? _locationSubscription;
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
    ref.watch(driverRealtimeProvider);
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
    _locationSubscription ??= _location!.updates.listen((update) {
      if (update.point == null) {
        debugPrint(
          'Driver location unavailable: '
          '${update.errorMessage ?? 'unknown location error'}',
        );
        return;
      }
      _pendingLocation = update;
      unawaited(_drainLocationQueue(repo, cancelToken));
    });
    // 定位回调经 WebSocket 上报，订单列表由服务端事件触发刷新。
    _location!.start(interval: const Duration(seconds: 2));
  }

  void _disposeTasks() {
    _online = false;
    _pauseTasks();
    _lifecycleListener?.dispose();
    _lifecycleListener = null;
    unawaited(_stopForegroundService());
  }

  void _pauseTasks() {
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
    if (!identical(_locationCancelToken, cancelToken)) return;
    if (_sendingLocation) return;
    final update = _pendingLocation;
    if (update?.point == null) return;
    _pendingLocation = null;
    if (!update!.isUsable()) return;
    _sendingLocation = true;
    try {
      await repo
          .reportLocation(
            point: update.point!,
            recordedTime: update.recordedTime ?? DateTime.now(),
            accuracyM: update.accuracyM,
            speedMps: update.speedMps,
            bearing: update.bearing,
            cancelToken: cancelToken,
            realtime: ref.read(driverRealtimeProvider),
          )
          .timeout(const Duration(seconds: 8));
      _locationFailureCount = 0;
      _locationRetryTimer?.cancel();
      _locationRetryTimer = null;
    } on Object catch (error) {
      if (!identical(_locationCancelToken, cancelToken)) return;
      if (error is ApiException && error.cancelled) return;
      // Keep the newest callback if one arrived while this request was in
      // flight; otherwise retry the failed point once connectivity returns.
      _pendingLocation ??= update;
      _locationFailureCount++;
      debugPrint('Driver location report failed; retrying: $error');
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
    if (!identical(_locationCancelToken, cancelToken)) return;
    if (_locationRetryTimer != null) return;
    final seconds = (1 << (_locationFailureCount.clamp(0, 5))).clamp(1, 30);
    _locationRetryTimer = Timer(Duration(seconds: seconds), () {
      if (!identical(_locationCancelToken, cancelToken)) return;
      _locationRetryTimer = null;
      unawaited(_drainLocationQueue(repo, cancelToken));
    });
  }
}

final pendingOrdersProvider = FutureProvider.autoDispose<OrderPage>((ref) {
  ref.watch(driverAuthProvider);
  // Work status and pushed changes both invalidate this query; no refresh timer.
  final online = ref.watch(driverWorkProvider).value ?? false;
  if (!online) {
    return const OrderPage(page: 1, size: 20, total: 0, records: []);
  }
  final cancelToken = CancelToken();
  ref.onDispose(() => cancelToken.cancel('pending orders provider disposed'));
  return driverOrderQuery(
    ref,
    () => ref
        .read(driverRepositoryProvider)
        .pendingOrders(cancelToken: cancelToken),
  );
});

final driverOrdersProvider = FutureProvider.autoDispose<OrderPage>((ref) {
  ref.watch(driverAuthProvider);
  final cancelToken = CancelToken();
  ref.onDispose(() => cancelToken.cancel('driver orders provider disposed'));
  return driverOrderQuery(
    ref,
    () => ref.read(driverRepositoryProvider).orders(cancelToken: cancelToken),
  );
});
