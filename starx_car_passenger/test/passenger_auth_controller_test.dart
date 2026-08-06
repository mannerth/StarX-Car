import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';
import 'package:starx_car_passenger/core/providers.dart';
import 'package:starx_car_passenger/features/auth/auth_controller.dart';

void main() {
  test(
    'restores only a passenger session and saves a matching login',
    () async {
      final store = MemorySessionStore(
        const AuthSession(
          accessToken: 'old-access',
          refreshToken: 'old-refresh',
          userId: 7,
          userType: 'driver',
        ),
      );
      final api = AuthApiClient(_loginResponse('passenger'));
      final container = ProviderContainer(
        overrides: [
          sessionStoreProvider.overrideWithValue(store),
          apiClientProvider.overrideWithValue(api),
        ],
      );
      addTearDown(container.dispose);

      expect(await container.read(passengerAuthProvider.future), isNull);
      await container
          .read(passengerAuthProvider.notifier)
          .login(username: 'alice', password: 'secret');

      expect(store.value?.userType, 'passenger');
      expect(store.value?.userId, 42);
      expect(api.path, '/auth/login');
    },
  );

  test('rejects a non-passenger login without persisting it', () async {
    final store = MemorySessionStore();
    final container = ProviderContainer(
      overrides: [
        sessionStoreProvider.overrideWithValue(store),
        apiClientProvider.overrideWithValue(
          AuthApiClient(_loginResponse('driver')),
        ),
      ],
    );
    addTearDown(container.dispose);

    await container.read(passengerAuthProvider.future);
    await expectLater(
      container
          .read(passengerAuthProvider.notifier)
          .login(username: 'driver-account', password: 'secret'),
      throwsA(isA<ApiException>()),
    );

    expect(
      container.read(passengerAuthProvider),
      isA<AsyncData<AuthSession?>>().having(
        (state) => state.value,
        'value',
        isNull,
      ),
    );
    expect(store.value, isNull);
  });

  test('keeps the session gate stable when login fails', () async {
    final store = MemorySessionStore();
    final container = ProviderContainer(
      overrides: [
        sessionStoreProvider.overrideWithValue(store),
        apiClientProvider.overrideWithValue(FailingAuthApiClient()),
      ],
    );
    addTearDown(container.dispose);

    await container.read(passengerAuthProvider.future);
    await expectLater(
      container
          .read(passengerAuthProvider.notifier)
          .login(username: 'alice', password: 'wrong'),
      throwsA(isA<ApiException>()),
    );

    expect(
      container.read(passengerAuthProvider),
      isA<AsyncData<AuthSession?>>().having(
        (state) => state.value,
        'value',
        isNull,
      ),
    );
  });
}

Map<String, dynamic> _loginResponse(String type) => {
  'accessToken': 'access-$type',
  'refreshToken': 'refresh-$type',
  'user': {'id': 42, 'type': type},
};

class MemorySessionStore extends SessionStore {
  MemorySessionStore([this.value]) : super();

  AuthSession? value;

  @override
  Future<AuthSession?> read() async => value;

  @override
  Future<void> save(AuthSession session) async => value = session;

  @override
  Future<void> clear() async => value = null;
}

class AuthApiClient extends ApiClient {
  AuthApiClient(this.response)
    : super(baseUrl: 'http://test', sessionStore: SessionStore());

  final Map<String, dynamic> response;
  String? path;

  @override
  Future<dynamic> post(
    String requestPath, {
    Object? data,
    CancelToken? cancelToken,
  }) async {
    path = requestPath;
    return response;
  }
}

class FailingAuthApiClient extends ApiClient {
  FailingAuthApiClient()
    : super(baseUrl: 'http://test', sessionStore: SessionStore());

  @override
  Future<dynamic> post(
    String requestPath, {
    Object? data,
    CancelToken? cancelToken,
  }) async {
    throw const ApiException('用户名或密码错误。', statusCode: 401);
  }
}
