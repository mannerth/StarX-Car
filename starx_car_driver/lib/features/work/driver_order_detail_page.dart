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
      return ref.read(driverRepositoryProvider).detail(orderNo);
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
        data: _content,
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
        SizedBox(
          height: 260,
          child: AmapMapView(
            initialPoint: order.pickup.location,
            markers: [order.pickup.location, order.destination.location],
          ),
        ),
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
          _InfoRow(
            label: '乘客手机尾号',
            value: order.passengerPhoneTail ?? '未提供',
          ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: _busy ? null : () => _confirmStartService(order),
            icon: const Icon(Icons.play_arrow),
            label: const Text('开始服务'),
          ),
        ],
        if (order.status == OrderStatus.inService)
          FilledButton.icon(
            onPressed: _busy ? null : () => _confirmComplete(order.orderNo),
            icon: const Icon(Icons.check_circle_outline),
            label: const Text('完成并结算'),
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
  OrderStatus.pending => '等待乘客订单',
  OrderStatus.accepted => '前往上车点',
  OrderStatus.driverArrived => '等待乘客上车',
  OrderStatus.inService => '服务进行中',
  OrderStatus.completed => '已完成',
  OrderStatus.cancelled => '已取消',
  OrderStatus.unknown => '状态更新中',
};

int _minutes(int seconds) => (seconds / 60).ceil();


