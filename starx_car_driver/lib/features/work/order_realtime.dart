import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';
import '../../core/providers.dart';
import '../auth/auth_controller.dart';

final driverRealtimeProvider = Provider.autoDispose<RealtimeClient>((ref) {
  ref.watch(driverAuthProvider);
  final client = RealtimeClient(
    baseUrl: apiBaseUrl,
    api: ref.read(apiClientProvider),
  );
  ref.onDispose(() => unawaited(client.dispose()));
  client.start();
  return client;
});

final driverOrderChangesProvider = StreamProvider.autoDispose<int>((
  ref,
) async* {
  var revision = 0;
  await for (final event in ref.watch(driverRealtimeProvider).events) {
    if (event['type'] == 'ready' || event['type'] == 'orders.changed') {
      yield ++revision;
    }
  }
});

/// Preserve slow reads and perform one trailing read for changes received in flight.
Future<T> driverOrderQuery<T>(Ref ref, Future<T> Function() load) async {
  var loading = true;
  var dirty = false;
  ref.listen(driverOrderChangesProvider, (_, next) {
    if (!next.hasValue) return;
    if (loading) {
      dirty = true;
    } else {
      ref.invalidateSelf();
    }
  });
  try {
    return await load();
  } finally {
    loading = false;
    if (dirty) {
      Timer.run(() {
        if (ref.mounted) ref.invalidateSelf();
      });
    }
  }
}
