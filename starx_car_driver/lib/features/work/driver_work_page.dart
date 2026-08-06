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
    return Scaffold(
      appBar: AppBar(title: const Text('听单工作台')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SwitchListTile.adaptive(
            title: Text(online ? '正在听单' : '已收车'),
            subtitle: Text(online ? '位置上报与待抢订单刷新已开启' : '上线前请确认当前车辆和定位权限'),
            value: online,
            onChanged: work.isLoading
                ? null
                : (value) =>
                      ref.read(driverWorkProvider.notifier).setOnline(value),
          ),
          if (work.hasError)
            Text(
              work.error is ApiException
                  ? (work.error as ApiException).message
                  : '工作状态加载失败',
            ),
          if (locationError != null) ...[
            const SizedBox(height: 8),
            Text(
              locationError,
              style: TextStyle(color: Colors.orange.shade800),
            ),
          ],
          const Divider(),
          Text('待抢订单', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          if (!online)
            const Text('上线后显示附近可抢订单。')
          else
            pending.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (error, _) =>
                  Text(error is ApiException ? error.message : '待抢订单加载失败'),
              data: (page) => page.records.isEmpty
                  ? const Text('暂无待抢订单')
                  : Column(
                      children: [
                        for (final order in page.records)
                          _PendingOrderTile(order: order),
                      ],
                    ),
            ),
          const Divider(height: 32),
          Text('我的进行中订单', style: Theme.of(context).textTheme.titleLarge),
          orders.when(
            loading: () => const Padding(
              padding: EdgeInsets.all(12),
              child: CircularProgressIndicator(),
            ),
            error: (error, _) =>
                Text(error is ApiException ? error.message : '订单加载失败'),
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
      ),
    );
  }
}

class _ActiveOrderTile extends ConsumerStatefulWidget {
  const _ActiveOrderTile({required this.order});
  final OrderSummary order;

  @override
  ConsumerState<_ActiveOrderTile> createState() => _ActiveOrderTileState();
}

class _ActiveOrderTileState extends ConsumerState<_ActiveOrderTile> {
  bool _loading = false;

  @override
  Widget build(BuildContext context) {
    final order = widget.order;
    final repo = ref.read(driverRepositoryProvider);
    final action = switch (order.status) {
      OrderStatus.accepted => (
        '确认到达',
        () async {
          await repo.arrive(order.orderNo);
          return null;
        },
      ),
      OrderStatus.driverArrived => (
        '开始服务',
        () async {
          await repo.startService(order.orderNo);
          return null;
        },
      ),
      OrderStatus.inService => ('完成并结算', () => repo.complete(order.orderNo)),
      _ => null,
    };
    return Card(
      child: ListTile(
        title: Text('${order.pickupName} -> ${order.destinationName}'),
        subtitle: Text('订单 ${order.orderNo}'),
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute<void>(
            builder: (_) => DriverOrderDetailPage(orderNo: order.orderNo),
          ),
        ),
        trailing: action == null
            ? null
            : FilledButton(
                onPressed: _loading
                    ? null
                    : () async {
                        setState(() => _loading = true);
                        try {
                          final result = await action.$2();
                          if (result is OrderCompletionResult && mounted) {
                            await _showCompletionResult(result);
                          }
                          ref.invalidate(driverOrdersProvider);
                          ref.invalidate(pendingOrdersProvider);
                        } on ApiException catch (error) {
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
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
                    : Text(action.$1),
              ),
      ),
    );
  }

  Future<void> _showCompletionResult(OrderCompletionResult result) async {
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
  Widget build(BuildContext context) => ListTile(
    title: Text(
      '${widget.order.pickupName} -> ${widget.order.destinationName}',
    ),
    subtitle: Text('预估 ¥${widget.order.totalFare}'),
    trailing: FilledButton(
      onPressed: _loading
          ? null
          : () async {
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
                      builder: (_) =>
                          DriverOrderDetailPage(orderNo: widget.order.orderNo),
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
  );
}

int _minutes(int seconds) => (seconds / 60).ceil();
