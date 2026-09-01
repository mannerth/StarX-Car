import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';

import '../auth/auth_controller.dart';
import '../home/driver_repository.dart';
import 'driver_work_controller.dart';

final driverOrderDetailProvider = FutureProvider.autoDispose
    .family<OrderDetail, String>((ref, orderNo) {
      ref.watch(driverAuthProvider);
      final cancelToken = CancelToken();
      ref.onDispose(
        () => cancelToken.cancel('driver order detail provider disposed'),
      );
      return ref
          .read(driverRepositoryProvider)
          .detail(orderNo, cancelToken: cancelToken);
    });

/// Key for the driver order map route planner. The status and coordinates are
/// part of the key so a status transition (for example ACCEPTED -> IN_SERVICE)
/// starts a fresh planner for the new leg without reusing stale routes.
typedef _DriverRouteRequest = ({
  String orderNo,
  OrderStatus status,
  double pickupLat,
  double pickupLng,
  double destLat,
  double destLng,
});

/// Map data for the driver's own order screen: the latest device position and
/// the planned driving route for the current leg (to pickup or to destination).
class _DriverOrderRouteData {
  const _DriverOrderRouteData({
    required this.status,
    this.driverLocation,
    this.route,
    this.routeError,
    this.locationError,
    this.routing = false,
  });

  final OrderStatus status;
  final GeoPoint? driverLocation;
  final AmapDrivingRoute? route;
  final String? routeError;
  final String? locationError;
  final bool routing;
}

final driverOrderRouteProvider = StreamProvider.autoDispose
    .family<_DriverOrderRouteData, _DriverRouteRequest>((ref, request) async* {
      ref.watch(driverAuthProvider);
      final routeService = const AmapRouteService();
      final locationService = AmapLocationService();
      ref.onDispose(() => unawaited(locationService.dispose()));
      final pickup = GeoPoint(
        latitude: request.pickupLat,
        longitude: request.pickupLng,
      );
      final destination = GeoPoint(
        latitude: request.destLat,
        longitude: request.destLng,
      );
      GeoPoint? driverLocation;
      String? locationError;
      GeoPoint? plannedOrigin;
      DateTime? plannedAt;
      AmapDrivingRoute? route;
      String? routeError;
      var planning = false;
      yield _DriverOrderRouteData(status: request.status);
      locationService.start(interval: const Duration(seconds: 2));
      await for (final update in locationService.updates) {
        final point = update.point;
        if (point != null) {
          driverLocation = point;
          locationError = null;
        } else {
          locationError = update.errorMessage ?? '无法获取当前位置，请检查定位权限和 GPS。';
          if (driverLocation == null) {
            yield _DriverOrderRouteData(
              status: request.status,
              locationError: locationError,
            );
            continue;
          }
        }
        final target = switch (request.status) {
          OrderStatus.accepted || OrderStatus.driverArrived => pickup,
          OrderStatus.inService => destination,
          _ => null,
        };
        final showRoute =
            request.status == OrderStatus.accepted ||
            request.status == OrderStatus.inService;
        if (showRoute && target != null && !planning) {
          final now = DateTime.now();
          final distanceMoved = plannedOrigin == null
              ? double.infinity
              : _distanceM(plannedOrigin, driverLocation);
          final secondsSincePlan = plannedAt == null
              ? double.infinity
              : now.difference(plannedAt).inSeconds;
          final shouldRefresh = route == null
              ? plannedAt == null || secondsSincePlan >= 30
              : distanceMoved >= 100 || secondsSincePlan >= 30;
          if (shouldRefresh) {
            planning = true;
            plannedAt = now;
            try {
              route = await routeService.planDriving(
                origin: driverLocation,
                destination: target,
              );
              plannedOrigin = driverLocation;
              routeError = null;
            } on Object {
              routeError = '路线规划暂不可用，请稍后重试';
            } finally {
              planning = false;
            }
          }
        } else if (!showRoute) {
          route = null;
          plannedOrigin = null;
          plannedAt = null;
          routeError = null;
        }
        yield _DriverOrderRouteData(
          status: request.status,
          driverLocation: driverLocation,
          route: route,
          routeError: routeError,
          locationError: locationError,
          routing: planning,
        );
      }
    });

/// Driver-facing detail and state transition screen for one order.
class DriverOrderDetailPage extends ConsumerStatefulWidget {
  const DriverOrderDetailPage({super.key, required this.orderNo});

  final String orderNo;

  @override
  ConsumerState<DriverOrderDetailPage> createState() =>
      _DriverOrderDetailPageState();
}

class _DriverOrderDetailPageState extends ConsumerState<DriverOrderDetailPage> {
  bool _busy = false;
  Timer? _refreshTimer;
  OrderStatus? _lastStatus;

  @override
  void initState() {
    super.initState();
    _refreshTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      final status = _lastStatus;
      if (status == null || !_isActive(status)) return;
      ref.invalidate(driverOrderDetailProvider(widget.orderNo));
      ref.invalidate(driverOrdersProvider);
    });
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(driverOrderDetailProvider(widget.orderNo));
    return Scaffold(
      appBar: AppBar(title: const Text('订单详情')),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _ErrorBody(
          message: error is ApiException ? error.message : '订单详情加载失败',
          onRetry: () =>
              ref.invalidate(driverOrderDetailProvider(widget.orderNo)),
        ),
        data: (order) {
          _lastStatus = order.status;
          return _content(order);
        },
      ),
    );
  }

  Widget _content(OrderDetail order) {
    final canNavigateToPickup = order.status == OrderStatus.accepted;
    final canNavigateToDestination = order.status == OrderStatus.inService;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(order.orderNo, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 4),
        Text(
          _statusLabel(order.status),
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 12),
        if (order.isScheduled && order.scheduledPickupTime != null)
          _InfoRow(
            label: '预约上车时间',
            value: _formatTime(order.scheduledPickupTime!),
          ),
        _DriverOrderMap(order: order),
        const SizedBox(height: 12),
        _PlaceRow(label: '上车点', place: order.pickup, icon: Icons.trip_origin),
        _PlaceRow(
          label: '目的地',
          place: order.destination,
          icon: Icons.location_on,
        ),
        const Divider(height: 28),
        _InfoRow(label: '预估距离', value: '${order.estimatedDistanceM} 米'),
        _InfoRow(
          label: '预估时长',
          value: '${_minutes(order.estimatedDurationS)} 分钟',
        ),
        _InfoRow(label: '当前金额', value: '¥${order.fare.totalFare}'),
        if (order.actualDurationS != null)
          _InfoRow(
            label: '实际服务时长',
            value: '${_minutes(order.actualDurationS!)} 分钟',
          ),
        if (order.waitingDurationS != null)
          _InfoRow(
            label: '等待时长',
            value: '${_minutes(order.waitingDurationS!)} 分钟',
          ),
        const SizedBox(height: 16),
        if (canNavigateToPickup)
          OutlinedButton.icon(
            onPressed: _busy ? null : () => _navigate(order.pickup),
            icon: const Icon(Icons.navigation_outlined),
            label: const Text('导航去上车点'),
          ),
        if (canNavigateToDestination)
          OutlinedButton.icon(
            onPressed: _busy ? null : () => _navigate(order.destination),
            icon: const Icon(Icons.navigation_outlined),
            label: const Text('导航去目的地'),
          ),
        if (order.status == OrderStatus.accepted)
          FilledButton.icon(
            onPressed: _busy
                ? null
                : () => _confirmAndRun(
                    '确认已到达上车点？',
                    () => ref
                        .read(driverRepositoryProvider)
                        .arrive(order.orderNo),
                  ),
            icon: const Icon(Icons.place_outlined),
            label: const Text('确认已到达'),
          ),
        if (order.status == OrderStatus.driverArrived) ...[
          const Divider(height: 8),
          FilledButton.icon(
            onPressed: _busy ? null : () => _confirmStartService(order),
            icon: const Icon(Icons.play_arrow),
            label: const Text('开始服务'),
          ),
        ],
        if (order.status == OrderStatus.inService) ...[
          FilledButton.icon(
            onPressed: _busy ? null : () => _confirmComplete(order.orderNo),
            icon: const Icon(Icons.check_circle_outline),
            label: const Text('完成并结算'),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: _busy ? null : () => _confirmTerminate(order),
            icon: const Icon(Icons.stop_circle_outlined),
            label: const Text('中途结束行程'),
          ),
        ],
        if (order.status == OrderStatus.accepted ||
            order.status == OrderStatus.driverArrived)
          OutlinedButton.icon(
            onPressed: _busy ? null : () => _confirmCancel(order),
            icon: const Icon(Icons.cancel_outlined),
            label: const Text('取消订单'),
          ),
        if (order.status == OrderStatus.completed ||
            order.status == OrderStatus.terminated)
          OutlinedButton.icon(
            onPressed: _busy ? null : () => _submitComplaint(order.orderNo),
            icon: const Icon(Icons.report_problem_outlined),
            label: const Text('发起投诉'),
          ),
      ],
    );
  }

  Future<void> _navigate(Place destination) async {
    try {
      final opened = await const AmapNavigationService().openDrivingRoute(
        destination: destination.location,
        destinationName: destination.name,
      );
      if (!opened && mounted) {
        _showMessage('未找到高德地图，请先安装高德地图应用。');
      }
    } on Object catch (error) {
      if (mounted) _showMessage(error.toString());
    }
  }

  Future<void> _confirmAndRun(
    String title,
    Future<void> Function() action,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(title),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('确认'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await _run(action);
  }

  Future<void> _confirmStartService(OrderDetail order) async {
    final expected = order.passengerPhoneTail;
    if (expected == null || expected.length != 4) {
      _showMessage('订单缺少乘客手机尾号，无法核对乘客身份。');
      return;
    }
    final controller = TextEditingController();
    final confirmed = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('核对乘客尾号'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('请向乘客确认手机尾号（后四位）并输入，核对一致后才能开始服务。'),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              autofocus: true,
              keyboardType: TextInputType.number,
              maxLength: 4,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: const InputDecoration(
                labelText: '乘客手机尾号',
                hintText: '例如 1234',
                border: OutlineInputBorder(),
              ),
              onSubmitted: (_) =>
                  Navigator.pop(dialogContext, controller.text.trim()),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () =>
                Navigator.pop(dialogContext, controller.text.trim()),
            child: const Text('确认开始服务'),
          ),
        ],
      ),
    );
    Future<void>.delayed(const Duration(milliseconds: 300), controller.dispose);
    final tail = confirmed?.trim() ?? '';
    if (tail.length != 4 || !mounted) return;
    if (tail != expected) {
      _showMessage('乘客手机尾号不一致，请与乘客重新核对后再开始服务。');
      return;
    }
    await _run(
      () => ref
          .read(driverRepositoryProvider)
          .startService(order.orderNo, phoneTail: tail),
    );
  }

  Future<void> _confirmComplete(String orderNo) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('完成并结算？'),
        content: const Text('完成后将按服务端锁定的计价规则结算，不能撤回。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('继续服务'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('确认完成'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _busy = true);
    try {
      final result = await ref.read(driverRepositoryProvider).complete(orderNo);
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('订单已完成'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('实际服务：${_minutes(result.actualDurationS)} 分钟'),
              Text('等待：${_minutes(result.waitingDurationS)} 分钟'),
              const SizedBox(height: 8),
              Text(
                '最终金额：¥${result.fare.totalFare}',
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ],
          ),
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('知道了'),
            ),
          ],
        ),
      );
      ref.invalidate(driverOrderDetailProvider(orderNo));
      ref.invalidate(driverOrdersProvider);
      ref.invalidate(pendingOrdersProvider);
    } on ApiException catch (error) {
      if (mounted) _showMessage(error.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _confirmCancel(OrderDetail order) async {
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
    if (confirmed != true || !mounted) return;
    await _run(
      () => ref
          .read(driverRepositoryProvider)
          .cancelOrder(order.orderNo, reason: value),
    );
  }

  Future<void> _submitComplaint(String orderNo) async {
    final content = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('发起投诉'),
        content: TextField(
          controller: content,
          maxLength: 1000,
          maxLines: 4,
          decoration: const InputDecoration(labelText: '请描述问题'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('提交'),
          ),
        ],
      ),
    );
    final value = content.text.trim();
    Future<void>.delayed(const Duration(milliseconds: 300), content.dispose);
    if (confirmed != true || value.isEmpty || !mounted) return;
    setState(() => _busy = true);
    try {
      await ref
          .read(driverRepositoryProvider)
          .createComplaint(
            orderNo,
            complaintType: 'ORDER_SERVICE',
            content: value,
          );
      if (mounted) _showMessage('投诉已提交，我们会在七天内处理。');
    } on ApiException {
      if (mounted) _showMessage('投诉提交失败，请稍后重试。');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _confirmTerminate(OrderDetail order) async {
    final detail = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('中途结束行程'),
        content: TextField(
          controller: detail,
          maxLength: 500,
          maxLines: 3,
          decoration: const InputDecoration(labelText: '结束说明（可选）'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('继续行程'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('确认结束'),
          ),
        ],
      ),
    );
    final value = detail.text;
    Future<void>.delayed(const Duration(milliseconds: 300), detail.dispose);
    if (confirmed != true || !mounted) return;
    setState(() => _busy = true);
    try {
      final result = await ref
          .read(driverRepositoryProvider)
          .terminateOrder(
            order.orderNo,
            reasonCode: 'DRIVER_REQUEST',
            reasonDetail: value,
          );
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('行程已结束'),
          content: Text(
            '实际里程：${result.billingDistanceM} 米\n'
            '服务时长：${_minutes(result.actualDurationS)} 分钟\n'
            '结算金额：¥${result.totalFare}',
          ),
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('知道了'),
            ),
          ],
        ),
      );
      ref.invalidate(driverOrderDetailProvider(order.orderNo));
      ref.invalidate(driverOrdersProvider);
      ref.invalidate(pendingOrdersProvider);
    } on ApiException catch (error) {
      if (mounted) {
        _showMessage(
          error.statusCode == 409 ? '当前无法结束行程，请先上报最新位置后重试。' : '行程结束失败，请稍后重试。',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
      ref.invalidate(driverOrderDetailProvider(widget.orderNo));
      ref.invalidate(driverOrdersProvider);
      ref.invalidate(pendingOrdersProvider);
    } on ApiException catch (error) {
      if (mounted) _showMessage(error.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _showMessage(String message) => ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text(message)));
}

bool _isActive(OrderStatus status) =>
    status == OrderStatus.accepted ||
    status == OrderStatus.driverArrived ||
    status == OrderStatus.inService;

class _DriverOrderMap extends ConsumerWidget {
  const _DriverOrderMap({required this.order});

  final OrderDetail order;

  _DriverRouteRequest get _request => (
    orderNo: order.orderNo,
    status: order.status,
    pickupLat: order.pickup.location.latitude,
    pickupLng: order.pickup.location.longitude,
    destLat: order.destination.location.latitude,
    destLng: order.destination.location.longitude,
  );

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final route = ref.watch(driverOrderRouteProvider(_request));
    return route.when(
      loading: () => const SizedBox(
        height: 260,
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (error, _) => SizedBox(
        height: 260,
        child: Center(
          child: Text(error is ApiException ? error.message : '地图路线加载失败，请稍后重试'),
        ),
      ),
      data: _buildMap,
    );
  }

  Widget _buildMap(_DriverOrderRouteData value) {
    final driverPoint = value.driverLocation;
    final markers = <GeoPoint>[
      ?driverPoint,
      order.pickup.location,
      order.destination.location,
    ];
    final markerColors = <Color>[
      if (driverPoint != null) Colors.blue,
      Colors.green.shade700,
      Colors.red.shade700,
    ];
    final routePoints = value.route?.points ?? const <GeoPoint>[];
    final viewportPoints = routePoints.isNotEmpty
        ? routePoints
        : <GeoPoint>[
            ?driverPoint,
            order.pickup.location,
            order.destination.location,
          ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          height: 260,
          child: AmapMapView(
            initialPoint: driverPoint ?? order.pickup.location,
            markers: markers,
            markerColors: markerColors,
            routePoints: routePoints,
            viewportPoints: viewportPoints,
          ),
        ),
        const SizedBox(height: 8),
        Text(_statusLine(value)),
      ],
    );
  }

  String _statusLine(_DriverOrderRouteData value) {
    if (value.locationError != null) return value.locationError!;
    if (value.routing) return '正在规划路线…';
    final route = value.route;
    if (route != null) {
      final leg = value.status == OrderStatus.inService ? '目的地' : '上车点';
      return '$leg · 约 ${route.distanceM} 米 · ${_minutes(route.durationS)} 分钟';
    }
    if (value.routeError != null) return value.routeError!;
    return value.status == OrderStatus.driverArrived
        ? '已到达上车点，等待乘客上车'
        : '正在获取当前位置…';
  }
}

class _PlaceRow extends StatelessWidget {
  const _PlaceRow({
    required this.label,
    required this.place,
    required this.icon,
  });
  final String label;
  final Place place;
  final IconData icon;

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

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: Row(
      children: [
        Expanded(child: Text(label)),
        Text(value),
      ],
    ),
  );
}

class _ErrorBody extends StatelessWidget {
  const _ErrorBody({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(message),
        const SizedBox(height: 12),
        OutlinedButton.icon(
          onPressed: onRetry,
          icon: const Icon(Icons.refresh),
          label: const Text('重试'),
        ),
      ],
    ),
  );
}

String _statusLabel(OrderStatus status) => switch (status) {
  OrderStatus.scheduled => '等待派单',
  OrderStatus.pending => '等待乘客订单',
  OrderStatus.accepted => '前往上车点',
  OrderStatus.driverArrived => '等待乘客上车',
  OrderStatus.inService => '服务进行中',
  OrderStatus.completed => '已完成',
  OrderStatus.cancelled => '已取消',
  OrderStatus.terminated => '已结束',
  OrderStatus.unknown => '状态更新中',
};

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

int _minutes(int seconds) => (seconds / 60).ceil();

String _formatTime(DateTime time) {
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(time.month)}-${two(time.day)} ${two(time.hour)}:${two(time.minute)}';
}
