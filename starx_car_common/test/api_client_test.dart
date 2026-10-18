import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';

void main() {
  group('ApiClient', () {
    test(
      'transport token refresh rejection clears session and reports expiry',
      () async {
        final store = _MemorySessionStore(
          _session('old-access', 'expired-refresh'),
        );
        final client = _client(
          store,
          (_) => _jsonResponse(401, {
            'code': 401,
            'message': 'refresh expired',
            'data': null,
          }),
        );
        await expectLater(
          client.accessToken(refresh: true),
          throwsA(
            isA<ApiException>().having(
              (error) => error.statusCode,
              'statusCode',
              401,
            ),
          ),
        );
        expect(store.current, isNull);
        expect(store.clears, 1);
      },
    );
    test('adds Bearer token and unwraps a successful response', () async {
      final store = _MemorySessionStore(_session('old-access', 'old-refresh'));
      late final RequestOptions request;
      final client = _client(store, (options) {
        request = options;
        return _jsonResponse(200, {
          'code': 0,
          'message': 'success',
          'data': {'id': 7},
        });
      });

      final data = await client.get('/users/me');

      expect(data, {'id': 7});
      expect(request.headers['Authorization'], 'Bearer old-access');
    });

    test('maps an error envelope to ApiException with trace ID', () async {
      final store = _MemorySessionStore(_session('access', 'refresh'));
      final client = _client(
        store,
        (_) => _jsonResponse(409, {
          'code': 409,
          'message': '当前存在未结束订单',
          'traceId': 'trace-409',
          'data': null,
        }),
      );

      await expectLater(
        client.post('/orders'),
        throwsA(
          isA<ApiException>()
              .having((error) => error.statusCode, 'statusCode', 409)
              .having((error) => error.traceId, 'traceId', 'trace-409')
              .having((error) => error.message, 'message', '当前存在未结束订单'),
        ),
      );
    });

    test('refreshes once and replays concurrent 401 requests', () async {
      final store = _MemorySessionStore(_session('old-access', 'old-refresh'));
      var refreshCalls = 0;
      final client = _client(store, (options) async {
        if (options.path == '/auth/refresh') {
          refreshCalls++;
          expect(options.headers.containsKey('Authorization'), isFalse);
          return _jsonResponse(200, {
            'code': 0,
            'data': {
              'accessToken': 'new-access',
              'refreshToken': 'new-refresh',
              'user': {'id': 7, 'type': 'passenger'},
            },
          });
        }
        if (options.headers['Authorization'] == 'Bearer old-access') {
          return _jsonResponse(401, {
            'code': 401,
            'message': 'access token expired',
            'data': null,
          });
        }
        expect(options.headers['Authorization'], 'Bearer new-access');
        return _jsonResponse(200, {
          'code': 0,
          'data': {'path': options.path},
        });
      });

      final results = await Future.wait([
        client.get('/orders/one'),
        client.get('/orders/two'),
      ]);

      expect(refreshCalls, 1);
      expect(results, [
        {'path': '/orders/one'},
        {'path': '/orders/two'},
      ]);
      expect(store.current?.accessToken, 'new-access');
      expect(store.saves, 1);
    });

    test('clears the local session when refresh fails', () async {
      final store = _MemorySessionStore(_session('old-access', 'old-refresh'));
      final client = _client(store, (options) {
        if (options.path == '/auth/refresh') {
          return _jsonResponse(401, {
            'code': 401,
            'message': 'refresh token expired',
            'data': null,
          });
        }
        return _jsonResponse(401, {
          'code': 401,
          'message': 'access token expired',
          'data': null,
        });
      });

      await expectLater(client.get('/users/me'), throwsA(isA<ApiException>()));

      expect(store.current, isNull);
      expect(store.clears, 1);
    });

    test(
      'maps an explicit cancel token to a cancellable ApiException',
      () async {
        final store = _MemorySessionStore(_session('access', 'refresh'));
        final client = _client(
          store,
          (_) => fail(
            'a request cancelled before dispatch must not reach the adapter',
          ),
        );
        final cancelToken = CancelToken()..cancel('user cancelled');

        await expectLater(
          client.get('/orders/slow', cancelToken: cancelToken),
          throwsA(
            isA<ApiException>()
                .having((error) => error.cancelled, 'cancelled', isTrue)
                .having((error) => error.message, 'message', '请求已取消。'),
          ),
        );
      },
    );
  });
}

ApiClient _client(
  _MemorySessionStore store,
  FutureOr<ResponseBody> Function(RequestOptions options) handler,
) {
  final dio = Dio(BaseOptions(baseUrl: 'https://example.test'));
  dio.httpClientAdapter = _FakeAdapter(handler);
  return ApiClient(
    baseUrl: 'https://example.test',
    sessionStore: store,
    dio: dio,
  );
}

AuthSession _session(String accessToken, String refreshToken) => AuthSession(
  accessToken: accessToken,
  refreshToken: refreshToken,
  userId: 7,
  userType: 'passenger',
);

ResponseBody _jsonResponse(int statusCode, Map<String, dynamic> body) =>
    ResponseBody.fromString(
      jsonEncode(body),
      statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );

class _MemorySessionStore extends SessionStore {
  _MemorySessionStore(this.current);

  AuthSession? current;
  int saves = 0;
  int clears = 0;

  @override
  Future<AuthSession?> read() async => current;

  @override
  Future<void> save(AuthSession session) async {
    current = session;
    saves++;
  }

  @override
  Future<void> clear() async {
    current = null;
    clears++;
  }
}

class _FakeAdapter implements HttpClientAdapter {
  _FakeAdapter(this._handler);

  final FutureOr<ResponseBody> Function(RequestOptions options) _handler;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async => _handler(options);

  @override
  void close({bool force = false}) {}
}
