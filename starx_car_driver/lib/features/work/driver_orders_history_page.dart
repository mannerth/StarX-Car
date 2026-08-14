import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';

import '../home/driver_repository.dart';
import 'driver_order_detail_page.dart';

/// Driver-side order history: completed/cancelled orders with status filter,
/// pagination and entry into the shared order detail page.
class DriverOrdersHistoryPage extends ConsumerStatefulWidget {
  const DriverOrdersHistoryPage({super.key});

  @override
  ConsumerState<DriverOrdersHistoryPage> createState() =>
      _DriverOrdersHistoryPageState();
}

class _DriverOrdersHistoryPageState
    extends ConsumerState<DriverOrdersHistoryPage> {
  static const _pageSize = 20;
  static const _filters = <OrderStatus?>[
    null,
    OrderStatus.completed,
    OrderStatus.cancelled,
  ];

  final _scrollController = ScrollController();
  final List<OrderSummary> _records = [];
  OrderStatus? _status;
  int _page = 0;
  int _total = 0;
  bool _loading = false;
  bool _loadingMore = false;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    WidgetsBinding.instance.addPostFrameCallback((_) => _load(reset: true));
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 200) {
      _loadMore();
    }
  }

  Future<void> _selectStatus(OrderStatus? status) async {
    if (_status == status) return;
    setState(() => _status = status);
    await _load(reset: true);
  }

  Future<void> _load({required bool reset}) async {
    if (_loading) return;
    setState(() {
      _loading = true;
      if (reset) _error = null;
    });
    try {
      final page = await ref.read(driverRepositoryProvider).orders(
        page: reset ? 1 : _page + 1,
        size: _pageSize,
        status: _status,
      );
      if (!mounted) return;
      setState(() {
        _records
          ..clear()
          ..addAll(page.records);
        _page = page.page;
        _total = page.total;
        _loading = false;
        _loadingMore = false;
      });
    } on Object catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _loadingMore = false;
        if (reset || _records.isEmpty) _error = error;
      });
    }
  }

  Future<void> _loadMore() async {
    if (_loading || _loadingMore || _records.length >= _total) return;
    setState(() => _loadingMore = true);
    try {
      final page = await ref.read(driverRepositoryProvider).orders(
        page: _page + 1,
        size: _pageSize,
        status: _status,
      );
      if (!mounted) return;
      setState(() {
        _records.addAll(page.records);
        _page = page.page;
        _total = page.total;
        _loadingMore = false;
      });
    } on Object {
      if (mounted) setState(() => _loadingMore = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('历史订单')),
      body: Column(
        children: [
          SizedBox(
            height: 56,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              children: [
                for (final status in _filters)
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
          Expanded(child: _body()),
        ],
      ),
    );
  }

  Widget _body() {
    if (_loading && _records.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null && _records.isEmpty) {
      return _LoadError(
        message: _error is ApiException
            ? (_error as ApiException).message
            : '历史订单加载失败，请稍后重试。',
        onRetry: () => _load(reset: true),
      );
    }
    if (_records.isEmpty) {
      return RefreshIndicator(
        onRefresh: () => _load(reset: true),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: const [
            SizedBox(height: 160),
            Center(child: Text('暂无历史订单')),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: () => _load(reset: true),
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
            title: Text('${order.pickupName} -> ${order.destinationName}'),
            subtitle: Text(
              '${_statusLabel(order.status)}  '
              '${order.estimatedFare ? '预估' : '已结算'} ¥${order.totalFare}',
            ),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => DriverOrderDetailPage(orderNo: order.orderNo),
              ),
            ),
          );
        },
      ),
    );
  }
}

String _filterLabel(OrderStatus? status) => switch (status) {
  null => '全部',
  OrderStatus.completed => '已完成',
  OrderStatus.cancelled => '已取消',
  _ => '其他',
};

IconData _statusIcon(OrderStatus status) => switch (status) {
  OrderStatus.completed => Icons.check_circle_outline,
  OrderStatus.cancelled => Icons.cancel_outlined,
  _ => Icons.receipt_long_outlined,
};

String _statusLabel(OrderStatus status) => switch (status) {
  OrderStatus.completed => '已完成',
  OrderStatus.cancelled => '已取消',
  _ => '进行中',
};

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