import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';

import '../auth/auth_controller.dart';
import 'driver_repository.dart';

final driverRatingsProvider = FutureProvider.autoDispose<RatingPage>((ref) {
  ref.watch(driverAuthProvider);
  return ref.read(driverRepositoryProvider).myRatings(size: 100);
});

class DriverRatingsPage extends ConsumerWidget {
  const DriverRatingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ratings = ref.watch(driverRatingsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('我的评价')),
      body: ratings.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                error is ApiException ? error.message : '评价加载失败，请稍后重试。',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () => ref.invalidate(driverRatingsProvider),
                icon: const Icon(Icons.refresh),
                label: const Text('重试'),
              ),
            ],
          ),
        ),
        data: (page) {
          final records = page.records;
          if (records.isEmpty) {
            return const Center(child: Text('还没有收到评价'));
          }
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(driverRatingsProvider),
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: records.length + 1,
              separatorBuilder: (_, _) => const Divider(height: 1),
              itemBuilder: (context, index) {
                if (index == 0) {
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                    child: Text(
                      '共 ${page.total} 条评价',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  );
                }
                final item = records[index - 1];
                return ListTile(
                  title: Row(
                    children: [
                      _RatingStars(value: item.rating ?? 0, size: 18),
                      const Spacer(),
                      Text('订单 ${item.orderNo}'),
                    ],
                  ),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (item.content?.isNotEmpty == true) ...[
                        const SizedBox(height: 4),
                        Text(item.content!),
                      ],
                      if (item.createdAt != null) ...[
                        const SizedBox(height: 4),
                        Text(
                          _formatRatingTime(item.createdAt!),
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ],
                  ),
                  isThreeLine: item.content?.isNotEmpty == true,
                );
              },
            ),
          );
        },
      ),
    );
  }
}

class _RatingStars extends StatelessWidget {
  const _RatingStars({required this.value, required this.size});

  final int value;
  final double size;

  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      for (var star = 1; star <= 5; star++)
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 1),
          child: Icon(
            star <= value ? Icons.star : Icons.star_border,
            size: size,
            color: star <= value ? Colors.amber : Colors.grey.shade400,
          ),
        ),
    ],
  );
}

String _formatRatingTime(DateTime value) =>
    value.toLocal().toString().replaceFirst('T', ' ').split('.').first;