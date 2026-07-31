import 'package:dio/dio.dart';

import '../auth/auth_session.dart';
import '../auth/session_store.dart';
import 'api_exception.dart';

/// Shared HTTP transport: API envelopes, Bearer token attachment and 401 refresh.
///
/// Business repositories stay in the passenger and driver applications.
class ApiClient {
  ApiClient({required String baseUrl, required SessionStore sessionStore})
    : _store = sessionStore,
      _dio = Dio(
        BaseOptions(baseUrl: baseUrl, contentType: Headers.jsonContentType),
      ) {
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final session = await _store.read();
          if (session != null) {
            options.headers['Authorization'] = 'Bearer ${session.accessToken}';
          }
          handler.next(options);
        },
        onError: (error, handler) async {
          if (error.response?.statusCode != 401 ||
              error.requestOptions.path == '/auth/refresh') {
            handler.next(error);
            return;
          }
          try {
            final refreshed = await _refreshSession();
            if (refreshed == null) {
              handler.next(error);
              return;
            }
            final request = error.requestOptions;
            request.headers['Authorization'] =
                'Bearer ${refreshed.accessToken}';
            handler.resolve(await _dio.fetch<dynamic>(request));
          } catch (_) {
            await _store.clear();
            handler.next(error);
          }
        },
      ),
    );
  }

  final Dio _dio;
  final SessionStore _store;
  Future<AuthSession?>? _refreshing;

  Future<dynamic> get(String path) => _request(() => _dio.get<dynamic>(path));
  Future<dynamic> post(String path, {Object? data}) =>
      _request(() => _dio.post<dynamic>(path, data: data));
  Future<dynamic> put(String path, {Object? data}) =>
      _request(() => _dio.put<dynamic>(path, data: data));
  Future<dynamic> delete(String path) =>
      _request(() => _dio.delete<dynamic>(path));

  Future<dynamic> _request(Future<Response<dynamic>> Function() request) async {
    try {
      final response = await request();
      return _unwrap(response.data);
    } on DioException catch (error) {
      final body = error.response?.data;
      if (body is Map<String, dynamic>) {
        throw ApiException(
          body['message'] as String? ?? '请求失败，请稍后重试。',
          statusCode: error.response?.statusCode,
          traceId: body['traceId'] as String?,
        );
      }
      throw ApiException(
        error.type == DioExceptionType.connectionError
            ? '无法连接服务器，请检查网络和服务地址。'
            : '请求失败，请稍后重试。',
      );
    }
  }

  dynamic _unwrap(dynamic body) {
    if (body is! Map<String, dynamic>) return body;
    if ((body['code'] as num?)?.toInt() != 0) {
      throw ApiException(
        body['message'] as String? ?? '请求失败。',
        traceId: body['traceId'] as String?,
      );
    }
    return body['data'];
  }

  Future<AuthSession?> _refreshSession() =>
      _refreshing ??= _doRefresh().whenComplete(() => _refreshing = null);

  Future<AuthSession?> _doRefresh() async {
    final session = await _store.read();
    if (session == null) return null;
    final response = await _dio.post<dynamic>(
      '/auth/refresh',
      data: {'refreshToken': session.refreshToken},
      options: Options(headers: {'Authorization': null}),
    );
    final data = _unwrap(response.data) as Map<String, dynamic>;
    final user = data['user'] as Map<String, dynamic>;
    final refreshed = AuthSession(
      accessToken: data['accessToken'] as String,
      refreshToken: data['refreshToken'] as String,
      userId: (user['id'] as num).toInt(),
      userType: user['type'] as String,
    );
    await _store.save(refreshed);
    return refreshed;
  }
}
