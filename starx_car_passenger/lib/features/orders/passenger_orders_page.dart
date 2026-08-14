import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:starx_car_common/starx_car_common.dart';

import '../../core/providers.dart';
import '../auth/auth_controller.dart';
import 'passenger_order_repository.dart';

final passengerOrdersProvider = FutureProvider.autoDispose
    .family<OrderPage, OrderStatus?>((ref, status) {
      ref.watch(passengerAuthProvider);
      final cancelToken = CancelToken();
      ref.onDispose(
        () => cancelToken.cancel('passenger orders provider disposed'),
      );
      return ref
          .read(passengerOrderRepositoryProvider)
          .list(status: status, cancelToken: cancelToken);
    });

final passengerOrderDetailPollIntervalProvider = Provider<Duration>(
  (ref) => const Duration(seconds: 4),
);

final passengerOrderDetailProvider = StreamProvider.autoDispose
    .family<OrderDetail, String>((ref, orderNo) async* {
      ref.watch(passengerAuthProvider);
      final repository = ref.read(passengerOrderRepositoryProvider);
      final cancelToken = CancelToken();
      ref.onDispose(() => cancelToken.cancel('order detail provider disposed'));
      var failureCount = 0;
      while (true) {
        await _waitForForeground();
        late final OrderDetail detail;
        try {
          detail = await repository.detail(orderNo, cancelToken: cancelToken);
          failureCount = 0;
        } on ApiException catch (error) {
          if (error.cancelled) return;
          if (error.statusCode == 404 || error.statusCode == 409) rethrow;
          failureCount++;
          if (failureCount >= _maxPollingFailures) rethrow;
          await _pollRetryDelay(failureCount);
          continue;
        } on Object {
          failureCount++;
          if (failureCount >= _maxPollingFailures) rethrow;
          await _pollRetryDelay(failureCount);
          continue;
        }
        yield detail;
        if (_isTerminalOrderStatus(detail.status)) return;
        await Future<void>.delayed(
          ref.read(passengerOrderDetailPollIntervalProvider),
        );
      }
    });

final passengerOrderRatingProvider = FutureProvider.autoDispose
    .family<OrderRating, String>((ref, orderNo) {
      ref.watch(passengerAuthProvider);
      return ref.read(passengerOrderRepositoryProvider).rating(orderNo);
    });

typedef _ApproachRequest = ({
  String orderNo,
  double pickupLat,
  double pickupLng,
});

/// Combined data for the driver-approaching phase: the latest driver
/// location, the driving route from the driver to the pickup, the passenger's
/// walking route to the pickup, and any planning/location errors.
class _PassengerApproachData {
  const _PassengerApproachData({
    required this.driverLocation,
    this.passengerLocation,
    this.driverRoute,
    this.walkingRoute,
    this.driverRouteError,
    this.walkingRouteError,
    this.locationError,
    this.nearPickup = false,
  });

  final DriverLocation driverLocation;
  final GeoPoint? passengerLocation;
  final AmapDrivingRoute? driverRoute;
  final AmapWalkingRoute? walkingRoute;
  final String? driverRouteError;
  final String? walkingRouteError;
  final String? locationError;
  final bool nearPickup;
}

final passengerApproachProvider = StreamProvider.autoDispose
    .family<_PassengerApproachData, _ApproachRequest>(
      (ref, request) async* {
        ref.watch(passengerAuthProvider);
        final repository = ref.read(passengerOrderRepositoryProvider);
        final routeService = const AmapRouteService();
        final cancelToken = CancelToken();
        final locationService = AmapLocationService();
        ref.onDispose(() {
          cancelToken.cancel('approach provider disposed');
          unawaited(locationService.dispose());
        });
        final pickup = GeoPoint(
          latitude: request.pickupLat,
          longitude: request.pickupLng,
        );
        GeoPoint? passengerLocation;
        bool located = false;
        bool locating = false;
        GeoPoint? plannedDriverOrigin;
        DateTime? plannedDriverAt;
        AmapDrivingRoute? driverRoute;
        String? driverRouteError;
        GeoPoint? plannedWalkOrigin;
        DateTime? plannedWalkAt;
        AmapWalkingRoute? walkingRoute;
        String? walkingRouteError;
        String? locationError;
        var failureCount = 0;
        while (true) {
          await _waitForForeground();
          if (!located && !locating) {
            locating = true;
            try {
              final update = await locationService.locateOnce();
              if (update.point != null) {
                passengerLocation = update.point;
                locationError = null;
              } else {
                locationError =
                    update.errorMessage ?? '无法获取当前位置，请检查定位权限和 GPS。';
              }
            } on Object {
              locationError = '无法获取当前位置，请检查定位权限和 GPS。';
            } finally {
              locating = false;
            }
            located = true;
          }
          late final DriverLocation driverLocation;
          try {
            driverLocation = await repository.driverLocation(
              request.orderNo,
              cancelToken: cancelToken,
            );
            failureCount = 0;
          } on ApiException catch (error) {
            if (error.cancelled) return;
            if (error.statusCode == 404 || error.statusCode == 409) return;
            failureCount++;
            if (failureCount >= _maxPollingFailures) rethrow;
            await _pollRetryDelay(failureCount);
            continue;
          } on Object {
            failureCount++;
            if (failureCount >= _maxPollingFailures) rethrow;
            await _pollRetryDelay(failureCount);
            continue;
          }
          final now = DateTime.now();
          final driverDistanceMoved = plannedDriverOrigin == null
              ? double.infinity
              : _distanceM(plannedDriverOrigin, driverLocation.point);
          final driverSecondsSincePlan = plannedDriverAt == null
              ? double.infinity
              : now.difference(plannedDriverAt).inSeconds;
          final shouldRefreshDriverRoute = driverRoute == null
              ? plannedDriverAt == null || driverSecondsSincePlan >= 5
              : driverDistanceMoved >= _approachDriverRefreshDistanceM ||
                    driverSecondsSincePlan >= _approachDriverRefreshSeconds;
          if (shouldRefreshDriverRoute) {
            plannedDriverAt = now;
            try {
              driverRoute = await routeService.planDriving(
                origin: driverLocation.point,
                destination: pickup,
              );
              plannedDriverOrigin = driverLocation.point;
              driverRouteError = null;
            } on Object {
              driverRouteError = '司机路线暂不可用';
            }
          }
          final passengerPoint = passengerLocation;
          final walkDistance = passengerPoint == null
              ? double.infinity
              : _distanceM(passengerPoint, pickup);
          final nearPickup = passengerPoint != null && walkDistance < 50;
          final walkSecondsSincePlan = plannedWalkAt == null
              ? double.infinity
              : now.difference(plannedWalkAt).inSeconds;
          final walkMoved = passengerPoint == null ||
              plannedWalkOrigin == null ||
              _distanceM(plannedWalkOrigin, passengerPoint) >= 100;
          final shouldRefreshWalkRoute = !nearPickup &&
              (walkingRoute == null ||
                  walkMoved ||
                  walkSecondsSincePlan >= 120);
          if (shouldRefreshWalkRoute && passengerPoint != null) {
            plannedWalkAt = now;
            try {
              walkingRoute = await routeService.planWalking(
                origin: passengerPoint,
                destination: pickup,
              );
              plannedWalkOrigin = passengerPoint;
              walkingRouteError = null;
            } on Object {
              walkingRouteError = '步行路线暂不可用';
            }
          }
          yield _PassengerApproachData(
            driverLocation: driverLocation,
            passengerLocation: passengerPoint,
            driverRoute: driverRoute,
            walkingRoute: walkingRoute,
            driverRouteError: driverRouteError,
            walkingRouteError: walkingRouteError,
            locationError: locationError,
            nearPickup: nearPickup,
          );
          await Future<void>.delayed(_passengerLocationPollInterval);
        }
      },
    );

final passengerRouteContextProvider = FutureProvider.autoDispose
    .family<RouteContext, String>((ref, orderNo) {
      ref.watch(passengerAuthProvider);
      final cancelToken = CancelToken();
      ref.onDispose(
        () => cancelToken.cancel('route context provider disposed'),
      );
      return ref
          .read(passengerOrderRepositoryProvider)
          .routeContext(orderNo, cancelToken: cancelToken);
    });

final passengerTripRouteProvider = StreamProvider.autoDispose
    .family<_PassengerRouteData, String>((ref, orderNo) async* {
      ref.watch(passengerAuthProvider);
      final repository = ref.read(passengerOrderRepositoryProvider);
      final routeService = const AmapRouteService();
      final cancelToken = CancelToken();
      ref.onDispose(
        () => cancelToken.cancel('route context provider disposed'),
      );
      GeoPoint? plannedOrigin;
      DateTime? plannedAt;
      AmapDrivingRoute? route;
      String? routeError;
      var contextFailureCount = 0;
      while (true) {
        await _waitForForeground();
        late final RouteContext context;
        try {
          context = await repository.routeContext(
            orderNo,
            cancelToken: cancelToken,
          );
          contextFailureCount = 0;
        } on ApiException catch (error) {
          if (error.cancelled) return;
          if (error.statusCode == 404 || error.statusCode == 409) rethrow;
          contextFailureCount++;
          if (contextFailureCount >= _maxPollingFailures) rethrow;
          await _pollRetryDelay(contextFailureCount);
          continue;
        } on Object {
          contextFailureCount++;
          if (contextFailureCount >= _maxPollingFailures) rethrow;
          await _pollRetryDelay(contextFailureCount);
          continue;
        }
        final now = DateTime.now();
        final distanceMoved = plannedOrigin == null
            ? double.infinity
            : _distanceM(plannedOrigin, context.driverLocation.point);
        final secondsSincePlan = plannedAt == null
            ? double.infinity
            : now.difference(plannedAt).inSeconds;
        // 司机位置每 2 秒轮询；路线在司机移动超过一定距离或超过间隔后重规划，
        // 上次规划失败且暂无路线时每 5 秒自动重试，大范围移动后尽快刷新路线与视野。
        final shouldRefresh = route == null
            ? plannedAt == null || secondsSincePlan >= 5
            : distanceMoved >= _routeRefreshDistanceM ||
                  secondsSincePlan >= _routeRefreshSeconds;
        if (shouldRefresh) {
          plannedAt = now;
          try {
            route = await routeService.planDriving(
              origin: context.driverLocation.point,
              destination: context.destination.location,
            );
            plannedOrigin = context.driverLocation.point;
            routeError = null;
          } on Object catch (error) {
            routeError = error is ApiException ? error.message : '路线服务暂不可用';
          }
        }
        yield _PassengerRouteData(
          context: context,
          route: route,
          routeError: routeError,
        );
        await Future<void>.delayed(_passengerLocationPollInterval);
      }
    });

class _PassengerRouteData {
  const _PassengerRouteData({
    required this.context,
    this.route,
    this.routeError,
  });

  final RouteContext context;
  final AmapDrivingRoute? route;
  final String? routeError;
}

const _orderStatusFilters = <OrderStatus?>[
  null,
  OrderStatus.pending,
  OrderStatus.accepted,
  OrderStatus.driverArrived,
  OrderStatus.inService,
  OrderStatus.completed,
  OrderStatus.cancelled,
];

class PassengerOrdersPage extends ConsumerStatefulWidget {
  const PassengerOrdersPage({super.key});

  @override
  ConsumerState<PassengerOrdersPage> createState() =>
      _PassengerOrdersPageState();
}

class _PassengerOrdersPageState extends ConsumerState<PassengerOrdersPage> {
  final _scrollController = ScrollController();
  List<OrderSummary> _records = const [];
  int _page = 0;
  int _total = 0;
  bool _loading = true;
  bool _loadingMore = false;
  Object? _error;
  OrderStatus? _status;
  CancelToken? _listCancelToken;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    WidgetsBinding.instance.addPostFrameCallback((_) => _reload());
  }

  @override
  void dispose() {
    _listCancelToken?.cancel('passenger orders page disposed');
    _scrollController
      ..removeListener(_onScroll)
      ..dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.extentAfter < 240) {
      _loadMore();
    }
  }

  Future<void> _reload() async {
    if (!mounted) return;
    _listCancelToken?.cancel('passenger orders reload');
    _listCancelToken = null;
    setState(() {
      _loading = true;
      _error = null;
      _records = const [];
      _page = 0;
      _total = 0;
    });
    ref.invalidate(passengerOrdersProvider(_status));
    try {
      final page = await ref.read(passengerOrdersProvider(_status).future);
      if (mounted) _applyPage(page, replace: true);
    } on Object catch (error) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = error;
        });
      }
    }
  }

  Future<void> _loadMore() async {
    if (_loading || _loadingMore || _page > 0 && _records.length >= _total) {
      return;
    }
    final cancelToken = CancelToken();
    _listCancelToken = cancelToken;
    setState(() => _loadingMore = true);
    try {
      final page = await ref
          .read(passengerOrderRepositoryProvider)
          .list(page: _page + 1, status: _status, cancelToken: cancelToken);
      if (mounted) _applyPage(page, replace: _page == 0);
    } on ApiException catch (error) {
      if (error.cancelled) return;
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    } finally {
      if (identical(_listCancelToken, cancelToken)) _listCancelToken = null;
      if (mounted) setState(() => _loadingMore = false);
    }
  }

  void _applyPage(OrderPage page, {required bool replace}) {
    setState(() {
      _records = replace ? page.records : [..._records, ...page.records];
      _page = page.page;
      _total = page.total;
      _loading = false;
      _error = null;
    });
  }

  Future<void> _selectStatus(OrderStatus? status) async {
    if (_status == status) return;
    setState(() => _status = status);
    await _reload();
  }

  @override
  Widget build(BuildContext context) {
    ref.listen<AsyncValue<OrderPage>>(passengerOrdersProvider(_status), (
      _,
      next,
    ) {
      next.whenData((page) {
        if (mounted && !_loadingMore) _applyPage(page, replace: true);
      });
    });
    final listBody = _loading && _records.isEmpty
        ? const Center(child: CircularProgressIndicator())
        : _error != null && _records.isEmpty
        ? _LoadError(
            message: _error is ApiException
                ? (_error as ApiException).message
                : '订单加载失败，请稍后重试。',
            onRetry: _reload,
          )
        : _records.isEmpty
        ? RefreshIndicator(
            onRefresh: _reload,
            child: ListView(
              children: const [SizedBox(height: 240), _EmptyOrders()],
            ),
          )
        : RefreshIndicator(
            onRefresh: _reload,
            child: ListView.separated(
              controller: _scrollController,
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: _records.length + (_loadingMore ? 1 : 0),
              separatorBuilder: (_, _) => const Divider(height: 1),
              itemBuilder: (context, index) {
                if (index >= _records.length) {
                  return const Padding(
                    padding: EdgeInsets.all(16),
                    child: Center(child: CircularProgressIndicator()),
                  );
                }
                final order = _records[index];
                return ListTile(
                  leading: Icon(_statusIcon(order.status)),
                  title: Text(
                    '${order.pickupName} -> ${order.destinationName}',
                  ),
                  subtitle: Text(
                    '${_statusLabel(order.status)}  ${order.estimatedFare ? '预估' : '已结算'} ¥${order.totalFare}',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) =>
                          PassengerOrderDetailPage(orderNo: order.orderNo),
                    ),
                  ),
                );
              },
            ),
          );
    return Scaffold(
      appBar: AppBar(
        title: const Text('我的行程'),
        actions: [
          IconButton(
            tooltip: '刷新',
            onPressed: _loading ? null : _reload,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: Column(
        children: [
          SizedBox(
            height: 56,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              children: [
                for (final status in _orderStatusFilters)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ChoiceChip(
                      label: Text(_filterLabel(status)),
                      selected: _status == status,
                      onSelected: (_) => _selectStatus(status),
                    ),
                  ),
              ],
            ),
          ),
          Expanded(child: listBody),
        ],
      ),
    );
  }
}

class PassengerOrderDetailPage extends ConsumerWidget {
  const PassengerOrderDetailPage({super.key, required this.orderNo});

  final String orderNo;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(passengerOrderDetailProvider(orderNo));
    return Scaffold(
      appBar: AppBar(title: const Text('行程详情')),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _LoadError(
          message: error is ApiException ? error.message : '订单详情加载失败，请稍后重试。',
          onRetry: () => ref.invalidate(passengerOrderDetailProvider(orderNo)),
        ),
        data: (order) => _OrderDetailContent(order: order),
      ),
    );
  }
}

class _OrderDetailContent extends ConsumerWidget {
  const _OrderDetailContent({required this.order});

  final OrderDetail order;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final canCancel =
        order.status == OrderStatus.pending ||
        order.status == OrderStatus.accepted;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          _statusLabel(order.status),
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 16),
        if (order.status == OrderStatus.accepted ||
            order.status == OrderStatus.driverArrived)
          _ApproachPanel(
            orderNo: order.orderNo,
            pickup: order.pickup,
            driverArrived: order.status == OrderStatus.driverArrived,
          ),
        if (order.status == OrderStatus.inService)
          _RoutePanel(orderNo: order.orderNo),
        if (order.driver != null) ...[
          const SizedBox(height: 12),
          _DriverInfoCard(driver: order.driver!),
        ],
        _PlaceRow(icon: Icons.trip_origin, label: '上车点', place: order.pickup),
        _PlaceRow(
          icon: Icons.location_on,
          label: '目的地',
          place: order.destination,
        ),
        const Divider(height: 32),
        _DetailRow(label: '预估距离', value: '${order.estimatedDistanceM} 米'),
        _DetailRow(
          label: '预估时长',
          value: '${_minutes(order.estimatedDurationS)} 分钟',
        ),
        if (order.waitingDurationS != null)
          _DetailRow(
            label: '等待时长',
            value: '${_minutes(order.waitingDurationS!)} 分钟',
          ),
        const Divider(height: 32),
        _DetailRow(label: '起步价', value: '¥${order.fare.baseFare}'),
        _DetailRow(label: '距离费', value: '¥${order.fare.distanceFare}'),
        _DetailRow(label: '行程时间费', value: '¥${order.fare.drivingTimeFare}'),
        _DetailRow(label: '等待费', value: '¥${order.fare.waitingFare}'),
        _DetailRow(
          label: order.estimatedFare ? '预估总价' : '最终总价',
          value: '¥${order.fare.totalFare}',
          emphasized: true,
        ),

        if (order.status == OrderStatus.completed) ...[
          const Divider(height: 32),
          _RatingPanel(orderNo: order.orderNo),
        ],
        if (order.events.isNotEmpty) ...[
          const Divider(height: 32),
          Text('行程动态', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          for (final event in order.events) _OrderEventRow(event: event),
        ],
        if (canCancel) ...[
          const SizedBox(height: 20),
          OutlinedButton.icon(
            onPressed: () => _confirmCancel(context, ref),
            icon: const Icon(Icons.cancel_outlined),
            label: const Text('取消订单'),
          ),
        ],
      ],
    );
  }

  Future<void> _confirmCancel(BuildContext context, WidgetRef ref) async {
    final reason = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('取消订单'),
        content: TextField(
          controller: reason,
          maxLength: 255,
          decoration: const InputDecoration(labelText: '取消原因（可选）'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('保留订单'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('确认取消'),
          ),
        ],
      ),
    );
    final value = reason.text;
    Future<void>.delayed(const Duration(milliseconds: 300), reason.dispose);
    if (confirmed != true || !context.mounted) return;
    try {
      await ref
          .read(passengerOrderRepositoryProvider)
          .cancel(order.orderNo, reason: value);
      ref.invalidate(passengerOrdersProvider);
      ref.invalidate(passengerOrderDetailProvider(order.orderNo));
      if (context.mounted) Navigator.pop(context);
    } on ApiException catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    }
  }
}

class _ApproachPanel extends ConsumerWidget {
  const _ApproachPanel({
    required this.orderNo,
    required this.pickup,
    required this.driverArrived,
  });

  final String orderNo;
  final Place pickup;
  final bool driverArrived;

  _ApproachRequest get _request => (
    orderNo: orderNo,
    pickupLat: pickup.location.latitude,
    pickupLng: pickup.location.longitude,
  );

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final approach = ref.watch(passengerApproachProvider(_request));
    return approach.when(
      loading: () => const LinearProgressIndicator(),
      error: (error, _) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(error is ApiException ? error.message : '接驾信息暂不可用，请稍后重试'),
          OutlinedButton.icon(
            onPressed: () => ref.invalidate(passengerApproachProvider(_request)),
            icon: const Icon(Icons.refresh),
            label: const Text('重试'),
          ),
        ],
      ),
      data: (value) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            height: 260,
            child: AmapMapView(
              initialPoint: value.driverLocation.point,
              markers: [pickup.location, value.driverLocation.point],
              markerColors: [Colors.green.shade700, Colors.blue],
              routePoints: value.driverRoute?.points ?? const [],
              routes: value.walkingRoute == null
                  ? const []
                  : [
                      AmapMapRouteLine(
                        points: value.walkingRoute!.points,
                        color: const Color(0xffff9800),
                      ),
                    ],
              viewportPoints: _approachViewport(value),
            ),
          ),
          const SizedBox(height: 8),
          Text(_approachStatus(value)),
          const SizedBox(height: 12),
        ],
      ),
    );
  }

  List<GeoPoint> _approachViewport(_PassengerApproachData value) {
    if (value.walkingRoute != null && value.walkingRoute!.points.isNotEmpty) {
      return value.walkingRoute!.points;
    }
    if (value.driverRoute != null && value.driverRoute!.points.isNotEmpty) {
      return value.driverRoute!.points;
    }
    return [pickup.location, value.driverLocation.point];
  }

  String _approachStatus(_PassengerApproachData value) {
    final driverEta = value.driverRoute;
    final walkRoute = value.walkingRoute;
    return <String>[
      if (driverArrived)
        '司机已到达上车点，请尽快前往上车'
      else if (driverEta != null)
        '司机预计 ${_minutes(driverEta.durationS)} 分钟到达'
      else if (value.driverRouteError != null)
        '司机路线：${value.driverRouteError}'
      else
        '正在计算司机到达时间…',
      if (value.nearPickup)
        '您已在上车点附近'
      else if (walkRoute != null)
        '您步行到上车点约 ${walkRoute.distanceM} 米 · ${_minutes(walkRoute.durationS)} 分钟'
      else if (value.walkingRouteError != null)
        '步行路线：${value.walkingRouteError}'
      else if (value.locationError != null)
        value.locationError!
      else
        '正在计算您到上车点的步行路线…',
      if (value.driverLocation.stale)
        '司机位置更新于 ${value.driverLocation.receivedTime ?? value.driverLocation.recordedTime ?? '未知'}，以上路线基于最后已知位置',
    ].where((line) => line.isNotEmpty).join('\n');
  }
}

class _RoutePanel extends ConsumerWidget {
  const _RoutePanel({required this.orderNo});
  final String orderNo;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final route = ref.watch(passengerTripRouteProvider(orderNo));
    return route.when(
      loading: () => const LinearProgressIndicator(),
      error: (error, _) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(error is ApiException ? error.message : '路线暂不可用，请稍后重试'),
          OutlinedButton.icon(
            onPressed: () =>
                ref.invalidate(passengerTripRouteProvider(orderNo)),
            icon: const Icon(Icons.refresh),
            label: const Text('重试'),
          ),
        ],
      ),
      data: (value) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            height: 240,
            child: AmapMapView(
              initialPoint: value.context.driverLocation.point,
              markers: [
                value.context.driverLocation.point,
                value.context.destination.location,
              ],
              markerColors: [Colors.blue, Colors.red.shade700],
              routePoints: value.route?.points ?? const [],
              // 视野跟随司机当前位置与目的地，位置每次轮询更新后地图立即重绘。
              viewportPoints: [
                value.context.driverLocation.point,
                value.context.destination.location,
              ],
            ),
          ),
          Text(
            value.context.driverLocation.stale
                ? '路线基于司机最后已知位置'
                : value.routeError ?? '行程路线已加载',
          ),
          if (value.route != null)
            Text(
              '路线约 ${value.route!.distanceM} 米，${_minutes(value.route!.durationS)} 分钟',
            ),
          if (value.routeError != null)
            OutlinedButton.icon(
              onPressed: () =>
                  ref.invalidate(passengerTripRouteProvider(orderNo)),
              icon: const Icon(Icons.refresh),
              label: const Text('重试路线规划'),
            ),
          const SizedBox(height: 12),
        ],
      ),
    );
  }
}

class _PlaceRow extends StatelessWidget {
  const _PlaceRow({
    required this.icon,
    required this.label,
    required this.place,
  });

  final IconData icon;
  final String label;
  final Place place;

  @override
  Widget build(BuildContext context) => ListTile(
    contentPadding: EdgeInsets.zero,
    leading: Icon(icon),
    title: Text(label),
    subtitle: Text(
      place.address?.isNotEmpty == true
          ? '${place.name}\n${place.address}'
          : place.name,
    ),
    isThreeLine: place.address?.isNotEmpty == true,
  );
}

class _OrderEventRow extends StatelessWidget {
  const _OrderEventRow({required this.event});

  final OrderEvent event;

  @override
  Widget build(BuildContext context) {
    final status = orderStatusFromJson(event.toStatus);
    final label = status == OrderStatus.unknown
        ? event.eventType ?? '状态更新'
        : _statusLabel(status);
    final time = event.createdTime?.toString().replaceFirst('T', ' ') ?? '时间未知';
    return ListTile(
      contentPadding: EdgeInsets.zero,
      dense: true,
      leading: const Icon(Icons.timeline),
      title: Text(label),
      subtitle: Text(
        [
          time,
          event.remark,
        ].whereType<String>().where((value) => value.isNotEmpty).join('\n'),
      ),
      isThreeLine: event.remark?.isNotEmpty == true,
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({
    required this.label,
    required this.value,
    this.emphasized = false,
  });

  final String label;
  final String value;
  final bool emphasized;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 5),
    child: Row(
      children: [
        Expanded(child: Text(label)),
        Text(
          value,
          style: emphasized ? Theme.of(context).textTheme.titleMedium : null,
        ),
      ],
    ),
  );
}

class _EmptyOrders extends StatelessWidget {
  const _EmptyOrders();

  @override
  Widget build(BuildContext context) => const Center(child: Text('暂时没有行程记录'));
}

class _LoadError extends StatelessWidget {
  const _LoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: const Text('重试'),
          ),
        ],
      ),
    ),
  );
}

IconData _statusIcon(OrderStatus status) => switch (status) {
  OrderStatus.pending => Icons.hourglass_top,
  OrderStatus.accepted => Icons.local_taxi,
  OrderStatus.driverArrived => Icons.place,
  OrderStatus.inService => Icons.directions_car,
  OrderStatus.completed => Icons.check_circle_outline,
  OrderStatus.cancelled => Icons.cancel_outlined,
  OrderStatus.unknown => Icons.help_outline,
};

String _statusLabel(OrderStatus status) => switch (status) {
  OrderStatus.pending => '等待接单',
  OrderStatus.accepted => '司机正在前往',
  OrderStatus.driverArrived => '司机已到达',
  OrderStatus.inService => '行程中',
  OrderStatus.completed => '已完成',
  OrderStatus.cancelled => '已取消',
  OrderStatus.unknown => '状态更新中',
};

String _filterLabel(OrderStatus? status) => status == null
    ? '全部'
    : switch (status) {
        OrderStatus.pending => '待接单',
        OrderStatus.accepted => '接驾中',
        OrderStatus.driverArrived => '司机已到',
        OrderStatus.inService => '行程中',
        OrderStatus.completed => '已完成',
        OrderStatus.cancelled => '已取消',
        OrderStatus.unknown => '状态更新中',
      };

int _minutes(int seconds) => (seconds / 60).ceil();

bool _isTerminalOrderStatus(OrderStatus status) =>
    status == OrderStatus.completed || status == OrderStatus.cancelled;

const _maxPollingFailures = 3;

/// Passenger-side driver location poll interval for the approach and trip
/// panels. Kept short so a moving driver position is reflected promptly.
const _passengerLocationPollInterval = Duration(seconds: 2);

/// Re-plan the driving route when the driver has moved this far from the
/// origin used by the last plan, or after this many seconds, so the route
/// line and map viewport follow the driver's current position.
const _routeRefreshDistanceM = 50;
const _routeRefreshSeconds = 5;
const _approachDriverRefreshDistanceM = 50;
const _approachDriverRefreshSeconds = 3;

Future<void> _pollRetryDelay(int failureCount) async {
  final exponent = math.min(failureCount - 1, 4);
  await Future<void>.delayed(Duration(seconds: 1 << exponent));
}

double _distanceM(GeoPoint first, GeoPoint second) {
  const earthRadiusM = 6371000.0;
  final lat1 = first.latitude * 3.141592653589793 / 180;
  final lat2 = second.latitude * 3.141592653589793 / 180;
  final dLat = lat2 - lat1;
  final dLng = (second.longitude - first.longitude) * 3.141592653589793 / 180;
  final a =
      (math.sin(dLat / 2) * math.sin(dLat / 2)) +
      (math.cos(lat1) *
          math.cos(lat2) *
          math.sin(dLng / 2) *
          math.sin(dLng / 2));
  return earthRadiusM * 2 * math.asin(math.sqrt(a.clamp(0, 1)));
}

Future<void> _waitForForeground() async {
  while (!_isForeground) {
    await Future<void>.delayed(const Duration(seconds: 1));
  }
}

bool get _isForeground =>
    WidgetsBinding.instance.lifecycleState == null ||
    WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;


/// 已完成订单的评价区：已评价时只读展示，未评价时提供 1-5 星与可选文字输入。
class _RatingPanel extends ConsumerStatefulWidget {
  const _RatingPanel({required this.orderNo});

  final String orderNo;

  @override
  ConsumerState<_RatingPanel> createState() => _RatingPanelState();
}

class _RatingPanelState extends ConsumerState<_RatingPanel> {
  final TextEditingController _content = TextEditingController();
  int _score = 5;
  bool _submitting = false;

  @override
  void dispose() {
    _content.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final rating = ref.watch(passengerOrderRatingProvider(widget.orderNo));
    return rating.when(
      loading: () => const Center(
        child: Padding(
          padding: EdgeInsets.all(8),
          child: SizedBox(
            width: 22,
            height: 22,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      ),
      error: (error, _) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('评价加载失败', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () =>
                ref.invalidate(passengerOrderRatingProvider(widget.orderNo)),
            icon: const Icon(Icons.refresh),
            label: const Text('重试'),
          ),
        ],
      ),
      data: (value) => value.rated
          ? _RatingDisplay(rating: value)
          : _RatingEditor(
              score: _score,
              contentController: _content,
              submitting: _submitting,
              onScoreChanged: (value) => setState(() => _score = value),
              onSubmit: _submit,
            ),
    );
  }

  Future<void> _submit() async {
    setState(() => _submitting = true);
    try {
      await ref
          .read(passengerOrderRepositoryProvider)
          .submitRating(widget.orderNo, rating: _score, content: _content.text);
      ref.invalidate(passengerOrderRatingProvider(widget.orderNo));
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('感谢您的评价')));
      }
    } on ApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }
}

class _RatingDisplay extends StatelessWidget {
  const _RatingDisplay({required this.rating});

  final OrderRating rating;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Row(
        children: [
          Text('我的评价', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(width: 12),
          _StarRow(score: rating.rating ?? 0, size: 22, onChanged: null),
        ],
      ),
      if (rating.content?.isNotEmpty == true) ...[
        const SizedBox(height: 6),
        Text(rating.content!),
      ],
      if (rating.createdAt != null) ...[
        const SizedBox(height: 6),
        Text(
          _formatRatingTime(rating.createdAt!),
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ],
    ],
  );
}

class _RatingEditor extends StatelessWidget {
  const _RatingEditor({
    required this.score,
    required this.contentController,
    required this.submitting,
    required this.onScoreChanged,
    required this.onSubmit,
  });

  final int score;
  final TextEditingController contentController;
  final bool submitting;
  final ValueChanged<int> onScoreChanged;
  final VoidCallback onSubmit;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('评价司机', style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: 8),
      _StarRow(score: score, size: 36, onChanged: onScoreChanged),
      const SizedBox(height: 8),
      TextField(
        controller: contentController,
        maxLength: 500,
        maxLines: 3,
        decoration: const InputDecoration(
          hintText: '说说本次行程体验（可选）',
          border: OutlineInputBorder(),
        ),
      ),
      const SizedBox(height: 8),
      SizedBox(
        width: double.infinity,
        child: FilledButton.icon(
          onPressed: submitting ? null : onSubmit,
          icon: const Icon(Icons.star),
          label: Text(submitting ? '提交中…' : '提交评价'),
        ),
      ),
    ],
  );
}

/// 1-5 星展示/选择行；onChanged 为空时仅展示不可交互。
class _StarRow extends StatelessWidget {
  const _StarRow({required this.score, required this.size, this.onChanged});

  final int score;
  final double size;
  final ValueChanged<int>? onChanged;

  Color _starColor(int star) =>
      star <= score ? Colors.amber : Colors.grey.shade400;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var star = 1; star <= 5; star++)
          if (onChanged == null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 2),
              child: Icon(
                star <= score ? Icons.star : Icons.star_border,
                size: size,
                color: _starColor(star),
              ),
            )
          else
            IconButton(
              onPressed: () => onChanged!(star),
              padding: EdgeInsets.zero,
              constraints: BoxConstraints(
                minWidth: size + 8,
                minHeight: size + 8,
              ),
              iconSize: size,
              icon: Icon(
                star <= score ? Icons.star : Icons.star_border,
                color: _starColor(star),
              ),
            ),
      ],
    );
  }
}

String _formatRatingTime(DateTime value) =>
    value.toLocal().toString().replaceFirst('T', ' ').split('.').first;


/// 已接单司机的信息卡：评分、脱敏手机号、车牌、车辆信息与联系司机按钮。
class _DriverInfoCard extends StatelessWidget {
  const _DriverInfoCard({required this.driver});

  final OrderDriver driver;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.person_outline),
              const SizedBox(width: 8),
              Text('接单司机', style: Theme.of(context).textTheme.titleMedium),
              const Spacer(),
              _StarRow(score: driver.rating?.round() ?? 0, size: 16, onChanged: null),
              const SizedBox(width: 4),
              Text(
                driver.rating == null
                    ? '暂无评分'
                    : driver.rating!.toStringAsFixed(1),
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ],
          ),
          const SizedBox(height: 8),
          _DriverInfoRow(
            icon: Icons.badge_outlined,
            label: '车牌号',
            value: driver.carNumber ?? '未提供',
          ),
          _DriverInfoRow(
            icon: Icons.directions_car_outlined,
            label: '车辆',
            value: _vehicleLabel(),
          ),
          _DriverInfoRow(
            icon: Icons.phone_outlined,
            label: '联系电话',
            value: driver.displayPhone ?? '未提供',
          ),
          const SizedBox(height: 8),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed:
                  driver.phoneNumber?.isNotEmpty == true ? () => _callDriver(context) : null,
              icon: const Icon(Icons.call),
              label: const Text('联系司机'),
            ),
          ),
        ],
      ),
    ),
  );

  String _vehicleLabel() {
    final parts = [
      driver.carBrand,
      driver.carColor,
      driver.carType,
    ].whereType<String>().where((value) => value.isNotEmpty).toList();
    return parts.isEmpty ? '未提供' : parts.join(' · ');
  }

  Future<void> _callDriver(BuildContext context) async {
    final phone = driver.phoneNumber;
    if (phone == null || phone.isEmpty) return;
    try {
      final opened = await launchUrl(Uri(scheme: 'tel', path: phone));
      if (!opened && context.mounted) {
        _showMessage(context, '无法打开拨号，请手动拨打司机电话：$phone');
      }
    } on Object {
      if (context.mounted) {
        _showMessage(context, '无法打开拨号，请手动拨打司机电话：$phone');
      }
    }
  }

  void _showMessage(BuildContext context, String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }
}

class _DriverInfoRow extends StatelessWidget {
  const _DriverInfoRow({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: Row(
      children: [
        Icon(icon, size: 18, color: Theme.of(context).colorScheme.primary),
        const SizedBox(width: 10),
        SizedBox(
          width: 64,
          child: Text(label, style: Theme.of(context).textTheme.bodyMedium),
        ),
        Expanded(
          child: Text(value, textAlign: TextAlign.end),
        ),
      ],
    ),
  );
}
