import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';
import '../../core/providers.dart';
import '../auth/auth_controller.dart';

final passengerRealtimeProvider = Provider.autoDispose<RealtimeClient>((ref) {
  ref.watch(passengerAuthProvider);
  final client = RealtimeClient(
    baseUrl: apiBaseUrl,
    api: ref.read(apiClientProvider),
  );
  ref.onDispose(() => unawaited(client.dispose()));
  client.start();
  return client;
});

final passengerOrderChangesProvider = StreamProvider.autoDispose<int>((
  ref,
) async* {
  var revision = 0;
  await for (final event in ref.watch(passengerRealtimeProvider).events) {
    if (event['type'] == 'ready' || event['type'] == 'orders.changed') {
      yield ++revision;
    }
  }
});

/// Release the subscription immediately on disposal, even during location/route planning.
Stream<Map<String, dynamic>> passengerOrderSnapshots(
  Ref ref,
  RealtimeClient client,
  String topic,
  String orderNo,
) {
  final output = StreamController<Map<String, dynamic>>();
  late final StreamSubscription<Map<String, dynamic>> subscription;
  subscription = client
      .watch(topic, orderNo)
      .listen(
        output.add,
        onError: (Object error, StackTrace stack) {
          // Older servers report the normal end of these order phases as 409.
          // Other topics and error codes retain their normal error handling.
          if ((topic == 'order.location' || topic == 'order.route') &&
              error is ApiException &&
              error.statusCode == 409) {
            unawaited(subscription.cancel());
            unawaited(output.close());
          } else {
            output.addError(error, stack);
          }
        },
        onDone: () => unawaited(output.close()),
      );
  output.onCancel = subscription.cancel;
  ref.onDispose(() {
    unawaited(subscription.cancel());
    unawaited(output.close());
  });
  return output.stream;
}

/// Coalesce pushed changes while a list read is in flight, then refresh once more.
Future<T> passengerOrderQuery<T>(Ref ref, Future<T> Function() load) async {
  var loading = true;
  var dirty = false;
  ref.listen(passengerOrderChangesProvider, (_, next) {
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
