import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';

void main() {
  test(
    'subscription closure completes normally and allows resubscribing without closing the socket',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final connected = Completer<WebSocket>();
      final frames = <Map<String, dynamic>>[];
      server.listen((request) async {
        final socket = await WebSocketTransformer.upgrade(request);
        connected.complete(socket);
        socket.listen((raw) {
          final frame = jsonDecode(raw as String) as Map<String, dynamic>;
          frames.add(frame);
          if (frame['type'] == 'auth') {
            socket.add(jsonEncode({'type': 'ready'}));
          }
          if (frame['type'] == 'subscribe') {
            socket.add(
              jsonEncode({
                'type': 'snapshot',
                'topic': frame['topic'],
                'orderNo': frame['orderNo'],
                'data': {'ok': true},
              }),
            );
          }
          if (frame['type'] == 'driver.location') {
            socket.add(jsonEncode({'type': 'ack', 'id': frame['id']}));
          }
        });
      });
      final client = RealtimeClient(
        baseUrl: 'http://127.0.0.1:${server.port}/api',
        api: TestApi(),
      );
      final errors = <Object>[];
      final first = Completer<void>();
      final reopened = Completer<void>();
      final done = Completer<void>();
      StreamSubscription<Map<String, dynamic>>? replacement;
      final subscription = client
          .watch('test.topic', 'ORD-1')
          .listen(
            (_) {
              first.complete();
            },
            onError: errors.add,
            onDone: () {
              replacement = client.watch('test.topic', 'ORD-1').listen((_) {
                reopened.complete();
              }, onError: errors.add);
              done.complete();
            },
          );
      addTearDown(() async {
        await subscription.cancel();
        await replacement?.cancel();
        await client.dispose();
        if (connected.isCompleted) await (await connected.future).close();
        await server.close(force: true);
      });
      await first.future.timeout(const Duration(seconds: 3));
      final socket = await connected.future;
      socket.add(
        jsonEncode({
          'type': 'subscription.closed',
          'topic': 'test.topic',
          'orderNo': 'ORD-1',
          'reason': 'resource_state_changed',
          'code': 409,
        }),
      );
      await done.future.timeout(const Duration(seconds: 3));
      await reopened.future.timeout(const Duration(seconds: 3));
      await client.request('driver.location', {'latitude': 31.2});
      expect(errors, isEmpty);
      expect(
        frames.where((frame) => frame['type'] == 'subscribe'),
        hasLength(2),
      );
      expect(frames.where((frame) => frame['type'] == 'unsubscribe'), isEmpty);
      expect(frames.where((frame) => frame['type'] == 'auth'), hasLength(1));
    },
  );

  test(
    'authenticates, receives pushed snapshots, acknowledges writes and resubscribes after expiry',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final sockets = <WebSocket>[];
      final frames = <Map<String, dynamic>>[];
      final api = TestApi();
      server.listen((request) async {
        expect(request.uri.query, isEmpty);
        final socket = await WebSocketTransformer.upgrade(request);
        sockets.add(socket);
        socket.listen((raw) {
          final frame = jsonDecode(raw as String) as Map<String, dynamic>;
          frames.add(frame);
          switch (frame['type']) {
            case 'auth':
              socket.add(jsonEncode({'type': 'ready'}));
            case 'subscribe':
              socket.add(
                jsonEncode({
                  'type': 'snapshot',
                  'topic': frame['topic'],
                  'orderNo': frame['orderNo'],
                  'data': {'revision': sockets.length},
                }),
              );
            case 'driver.location':
              socket.add(jsonEncode({'type': 'ack', 'id': frame['id']}));
            case 'ping':
              socket.add(jsonEncode({'type': 'pong'}));
          }
        });
      });
      final client = RealtimeClient(
        baseUrl: 'http://127.0.0.1:${server.port}/api',
        api: api,
      );
      final snapshots = <int>[];
      final first = Completer<void>();
      final reconnected = Completer<void>();
      final subscription = client.watch('test.topic', 'ORD-1').listen((data) {
        snapshots.add(data['revision'] as int);
        if (snapshots.length == 1) first.complete();
        if (snapshots.length == 2) reconnected.complete();
      });
      addTearDown(() async {
        await subscription.cancel();
        await client.dispose();
        for (final socket in sockets) {
          await socket.close();
        }
        await server.close(force: true);
      });
      await first.future.timeout(const Duration(seconds: 3));
      expect(frames.first, {'type': 'auth', 'token': 'initial-access'});
      await client.request('driver.location', {
        'latitude': 31.2,
        'longitude': 121.4,
      });
      await sockets.first.close(1008, 'expired');
      await reconnected.future.timeout(const Duration(seconds: 5));
      expect(api.refreshes, 1);
      expect(
        frames.where((f) => f['type'] == 'auth').last['token'],
        'refreshed-access',
      );
      expect(frames.where((f) => f['type'] == 'subscribe').length, 2);
      expect(snapshots, [1, 2]);
    },
  );
}

class TestApi extends ApiClient {
  TestApi() : super(baseUrl: 'http://test/api', sessionStore: SessionStore());
  int refreshes = 0;
  @override
  Future<String?> accessToken({bool refresh = false}) async {
    if (refresh) refreshes++;
    return refreshes == 0 ? 'initial-access' : 'refreshed-access';
  }
}
