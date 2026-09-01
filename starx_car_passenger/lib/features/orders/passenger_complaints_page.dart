import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import 'passenger_order_repository.dart';

class PassengerComplaintsPage extends ConsumerStatefulWidget {
  const PassengerComplaintsPage({super.key});

  @override
  ConsumerState<PassengerComplaintsPage> createState() =>
      _PassengerComplaintsPageState();
}

class _PassengerComplaintsPageState
    extends ConsumerState<PassengerComplaintsPage> {
  static const _pageSize = 20;
  final _scrollController = ScrollController();
  final List<PassengerComplaint> _records = [];
  int _page = 1;
  int _total = 0;
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    Future<void>.microtask(() => _load(reset: true));
  }

  @override
  void dispose() {
    _scrollController
      ..removeListener(_onScroll)
      ..dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.extentAfter < 240 &&
        !_loading &&
        !_loadingMore &&
        _records.length < _total) {
      _load(reset: false);
    }
  }

  Future<void> _load({required bool reset}) async {
    if (reset) {
      setState(() {
        _loading = true;
        _error = null;
      });
    } else {
      setState(() => _loadingMore = true);
    }
    try {
      final nextPage = reset ? 1 : _page + 1;
      final result = await ref
          .read(passengerOrderRepositoryProvider)
          .myComplaints(page: nextPage, size: _pageSize);
      if (!mounted) return;
      setState(() {
        if (reset) _records.clear();
        _records.addAll(result.records);
        _page = result.page;
        _total = result.total;
        _error = null;
      });
    } on Object {
      if (!mounted) return;
      setState(() => _error = '投诉记录加载失败，请稍后重试。');
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
          _loadingMore = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('我的投诉'),
      actions: [
        IconButton(
          onPressed: _loading ? null : () => _load(reset: true),
          tooltip: '刷新投诉进度',
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: _body(),
  );

  Widget _body() {
    if (_loading && _records.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null && _records.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_error!),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: () => _load(reset: true),
              icon: const Icon(Icons.refresh),
              label: const Text('重试'),
            ),
          ],
        ),
      );
    }
    if (_records.isEmpty) {
      return const Center(child: Text('还没有投诉记录'));
    }
    return RefreshIndicator(
      onRefresh: () => _load(reset: true),
      child: ListView.builder(
        controller: _scrollController,
        padding: const EdgeInsets.all(12),
        itemCount: _records.length + (_loadingMore || _error != null ? 1 : 0),
        itemBuilder: (context, index) {
          if (index == _records.length) {
            return Padding(
              padding: const EdgeInsets.all(16),
              child: _loadingMore
                  ? const Center(child: CircularProgressIndicator())
                  : Center(
                      child: TextButton.icon(
                        onPressed: () => _load(reset: false),
                        icon: const Icon(Icons.refresh),
                        label: const Text('加载更多失败，点击重试'),
                      ),
                    ),
            );
          }
          return Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: _ComplaintCard(complaint: _records[index]),
          );
        },
      ),
    );
  }
}

class _ComplaintCard extends StatelessWidget {
  const _ComplaintCard({required this.complaint});

  final PassengerComplaint complaint;

  @override
  Widget build(BuildContext context) {
    final statusColor = switch (complaint.status) {
      'RESOLVED' => Colors.green,
      'PROCESSING' => Colors.blue,
      _ => Colors.orange,
    };
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    '订单 ${complaint.orderNo}',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    _statusLabel(complaint.status),
                    style: TextStyle(color: statusColor),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              '${_typeLabel(complaint.complaintType)} · ${complaint.complaintNo}',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const Divider(height: 24),
            Text('投诉原因', style: Theme.of(context).textTheme.labelLarge),
            const SizedBox(height: 4),
            Text(complaint.content.isEmpty ? '未填写具体原因' : complaint.content),
            if (complaint.createdTime != null) ...[
              const SizedBox(height: 8),
              Text(
                '提交时间：${_formatTime(complaint.createdTime!)}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
            if (complaint.resolved) ...[
              const Divider(height: 24),
              Text('管理员处理结果', style: Theme.of(context).textTheme.labelLarge),
              const SizedBox(height: 6),
              _ResultRow(
                label: '处理方式',
                value: _resolutionLabel(complaint.resolutionType),
              ),
              _ResultRow(
                label: '管理员回复',
                value: complaint.resolutionContent?.trim().isNotEmpty == true
                    ? complaint.resolutionContent!.trim()
                    : '管理员未填写回复',
              ),
              if (_refundAmount(complaint.refundAmount) > 0)
                _ResultRow(
                  label: '退款金额',
                  value:
                      '¥${_refundAmount(complaint.refundAmount).toStringAsFixed(2)}',
                ),
              if (complaint.resolvedTime != null)
                _ResultRow(
                  label: '处理时间',
                  value: _formatTime(complaint.resolvedTime!),
                ),
            ] else ...[
              const SizedBox(height: 12),
              Text(
                complaint.status == 'PROCESSING'
                    ? '管理员正在处理，请稍后刷新查看结果。'
                    : '工单已提交，等待管理员处理。',
                style: TextStyle(color: statusColor),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _ResultRow extends StatelessWidget {
  const _ResultRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 3),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(width: 84, child: Text(label)),
        Expanded(child: Text(value)),
      ],
    ),
  );
}

String _statusLabel(String status) => switch (status) {
  'PROCESSING' => '处理中',
  'RESOLVED' => '已处理',
  _ => '待处理',
};

String _typeLabel(String type) => switch (type) {
  'ORDER_SERVICE' => '订单服务投诉',
  'SERVICE_ATTITUDE' => '服务态度',
  'ROUTE_OR_FARE' => '路线或费用',
  'SAFETY' => '安全问题',
  'NO_SHOW' => '未按约服务',
  'LOST_ITEM' => '遗失物品',
  _ => '其他投诉',
};

String _resolutionLabel(String? type) => switch (type) {
  'APOLOGY' => '道歉处理',
  'PARTIAL_REFUND' => '部分退款',
  'FULL_REFUND' => '全额退款',
  'REJECT' => '投诉驳回',
  _ => '已结案',
};

double _refundAmount(String value) => double.tryParse(value) ?? 0;

String _formatTime(DateTime value) =>
    value.toLocal().toString().replaceFirst('T', ' ').split('.').first;
