import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';
import 'driver_work_controller.dart';
import '../home/driver_repository.dart';
import 'driver_order_detail_page.dart';

class DriverWorkPage extends ConsumerWidget {
  const DriverWorkPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final work = ref.watch(driverWorkProvider);
    final pending = ref.watch(pendingOrdersProvider);
    final orders = ref.watch(driverOrdersProvider);
    final locationError = ref.watch(driverLocationErrorProvider);
    final online = work.value ?? false;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          color: online ? Theme.of(context).colorScheme.primaryContainer : Theme.of(context).colorScheme.surfaceContainerHighest,
          child: SwitchListTile.adaptive(
            contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
            secondary: Icon(online ? Icons.wifi_tethering : Icons.power_settings_new),
            title: Text(online ? '正在听单' : '已收车', style: const TextStyle(fontWeight: FontWeight.w700)),
            subtitle: Text(online ? '位置上报与待抢订单刷新已开启' : '上线前请确认当前车辆和定位权限'),
            value: online,
            onChanged: work.isLoading ? null : (value) => ref.read(driverWorkProvider.notifier).setOnline(value),
          ),
        ),
        if (work.hasError)
          Text(
            work.error is ApiException
                ? (work.error as ApiException).message
                : '工作状态加载失败',
          ),
        if (locationError != null) ...[
          const SizedBox(height: 8),
          Text(locationError, style: TextStyle(color: Colors.orange.shade800)),
        ],
        const SizedBox(height: 24),
        Text('待抢订单', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 4),
        Text('根据当前位置实时刷新', style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 8),
        if (!online)
          const Text('上线后显示附近可抢订单。')
        else
          pending.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (error, _) => _InlineLoadError(
              message: error is ApiException ? error.message : '待抢订单加载失败',
              onRetry: () => ref.invalidate(pendingOrdersProvider),
            ),
            data: (page) => page.records.isEmpty
                ? const Text('暂无待抢订单')
                : Column(
                    children: [
                      for (final order in page.records)
                        _PendingOrderTile(order: order),
                    ],
                  ),
          ),
        const SizedBox(height: 28),
        Text('我的进行中订单', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        orders.when(
          loading: () => const Padding(
            padding: EdgeInsets.all(12),
            child: CircularProgressIndicator(),
          ),
          error: (error, _) => _InlineLoadError(
            message: error is ApiException ? error.message : '订单加载失败',
            onRetry: () => ref.invalidate(driverOrdersProvider),
          ),
          data: (page) {
            final active = page.records
                .where(
                  (order) =>
                      order.status == OrderStatus.accepted ||
                      order.status == OrderStatus.driverArrived ||
                      order.status == OrderStatus.inService,
                )
                .toList();
            return active.isEmpty
                ? const Text('暂无进行中订单')
                : Column(
                    children: [
                      for (final order in active)
                        _ActiveOrderTile(order: order),
                    ],
                  );
          },
        ),
      ],
    );
  }
}

class _InlineLoadError extends StatelessWidget {
  const _InlineLoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Row(
    children: [
      Expanded(child: Text(message)),
      TextButton.icon(
        onPressed: onRetry,
        icon: const Icon(Icons.refresh),
        label: const Text('重试'),
      ),
    ],
  );
}

class _ActiveOrderTile extends StatelessWidget {
  const _ActiveOrderTile({required this.order});
  final OrderSummary order;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        title: Text('${order.pickupName} -> ${order.destinationName}'),
        subtitle: Text('${_statusLabel(order.status)} · 订单 ${order.orderNo}'),
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute<void>(
            builder: (_) => DriverOrderDetailPage(orderNo: order.orderNo),
          ),
        ),
        trailing: const Icon(Icons.chevron_right),
      ),
    );
  }
}

class _PendingOrderTile extends ConsumerStatefulWidget {
  const _PendingOrderTile({required this.order});
  final OrderSummary order;

  @override
  ConsumerState<_PendingOrderTile> createState() => _PendingOrderTileState();
}

class _PendingOrderTileState extends ConsumerState<_PendingOrderTile> {
  bool _loading = false;

  @override
  Widget build(BuildContext context) {
    final order = widget.order;
    final scheduled =
        order.isScheduled && order.scheduledPickupTime != null;
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: ListTile(
        contentPadding: const EdgeInsets.fromLTRB(16, 10, 12, 10),
        title: Row(
        children: [
          if (scheduled) ...[
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: Colors.blue.shade50,
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: Colors.blue.shade200),
              ),
              child: Text(
                '预约',
                style: Theme.of(context)
                    .textTheme
                    .labelSmall
                    ?.copyWith(color: Colors.blue.shade700),
              ),
            ),
            const SizedBox(width: 6),
          ],
          Expanded(
            child: Text(
              '${order.pickupName} -> ${order.destinationName}',
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
        subtitle: Text(
        scheduled
            ? '预约 ${_formatTime(order.scheduledPickupTime!)} 上车 · 预估 ¥${order.totalFare}'
            : '预估 ¥${order.totalFare}',
      ),
        trailing: FilledButton(
        onPressed: _loading
            ? null
            : () async {
                final confirmed = await _confirmWorkAction(context, '抢单');
                if (!confirmed || !context.mounted) return;
                final navigator = Navigator.of(context);
                final messenger = ScaffoldMessenger.of(context);
                setState(() => _loading = true);
                try {
                  await ref
                      .read(driverRepositoryProvider)
                      .acceptOrder(widget.order.orderNo);
                  ref.invalidate(pendingOrdersProvider);
                  ref.invalidate(driverOrdersProvider);
                  if (mounted && navigator.mounted) {
                    await navigator.push(
                      MaterialPageRoute<void>(
                        builder: (_) => DriverOrderDetailPage(
                          orderNo: widget.order.orderNo,
                        ),
                      ),
                    );
                  }
                } on ApiException catch (error) {
                  if (mounted && messenger.mounted) {
                    messenger.showSnackBar(
                      SnackBar(content: Text(error.message)),
                    );
                  }
                } finally {
                  if (mounted) setState(() => _loading = false);
                }
              },
        child: _loading
            ? const SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Text('抢单'),
        ),
      ),
    );
  }
}

String _statusLabel(OrderStatus status) => switch (status) {
  OrderStatus.accepted => '前往上车点',
  OrderStatus.driverArrived => '等待乘客上车',
  OrderStatus.inService => '服务中',
  _ => '进行中',
};

String _formatTime(DateTime time) {
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(time.month)}-${two(time.day)} ${two(time.hour)}:${two(time.minute)}';
}

Future<bool> _confirmWorkAction(BuildContext context, String action) async =>
    await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('$action？'),
        content: Text(
          action == '抢单' ? '确认承接这笔订单？接单后请及时前往上车地点。' : '确认更新当前订单的行程状态？',
        ),
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
    ) ??
    false;
