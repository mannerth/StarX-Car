import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'api_client.dart';
import 'api_exception.dart';

/// Role-neutral authenticated transport. Apps own topic names and workflows.
class RealtimeClient {
  RealtimeClient({required String baseUrl, required this.api})
    : _uri = Uri.parse(
        '${baseUrl.replaceFirst(RegExp(r'/+$'), '')}/realtime',
      ).replace(scheme: baseUrl.startsWith('https:') ? 'wss' : 'ws');

  final Uri _uri;
  final ApiClient api;
  final _events = StreamController<Map<String, dynamic>>.broadcast();
  final _topics = <String, StreamController<Map<String, dynamic>>>{};
  final _pending = <String, Completer<void>>{};
  WebSocket? _socket;
  Timer? _retry;
  Timer? _heartbeat;
  bool _disposed = false;
  bool _connecting = false;
  bool _ready = false;
  bool _refresh = false;
  int _failures = 0;
  int _sequence = 0;
  ApiException? _fatal;
  DateTime _lastMessage = DateTime.now();

  Stream<Map<String, dynamic>> get events => _events.stream;

  void start() {
    if (_fatal == null &&
        _socket == null &&
        !_ready &&
        !_connecting &&
        !_disposed) {
      unawaited(_connect());
    }
  }

  Stream<Map<String, dynamic>> watch(String topic, String orderNo) {
    if (_fatal != null) return Stream.error(_fatal!);
    final key = '$topic:$orderNo';
    final existing = _topics[key];
    if (existing != null) return existing.stream;
    late final StreamController<Map<String, dynamic>> controller;
    controller = StreamController<Map<String, dynamic>>.broadcast(
      onListen: () {
        start();
        if (_ready) {
          _send({'type': 'subscribe', 'topic': topic, 'orderNo': orderNo});
        }
      },
      onCancel: () {
        if (!identical(_topics[key], controller)) return;
        _topics.remove(key);
        if (_ready) {
          _send({'type': 'unsubscribe', 'topic': topic, 'orderNo': orderNo});
        }
        unawaited(controller.close());
      },
    );
    _topics[key] = controller;
    return controller.stream;
  }

  Future<void> request(String type, Map<String, dynamic> data) async {
    if (_fatal != null) throw _fatal!;
    if (!_ready) {
      start();
      // Location producers retain only the newest fix; never queue stale writes across reconnects.
      throw const ApiException('实时连接正在恢复，请稍后重试。');
    }
    if (_disposed || !_ready) throw const ApiException('实时连接已断开。');
    final id = '${++_sequence}';
    final completer = Completer<void>();
    _pending[id] = completer;
    _send({'type': type, 'id': id, 'data': data});
    try {
      await completer.future.timeout(const Duration(seconds: 8));
    } finally {
      _pending.remove(id);
    }
  }

  Future<void> _connect() async {
    if (_disposed || _connecting || _ready || _socket != null) return;
    _retry?.cancel();
    _connecting = true;
    try {
      final token = await api.accessToken(refresh: _refresh);
      _refresh = false;
      if (token == null || _disposed) return;
      final socket = await WebSocket.connect(
        _uri.toString(),
      ).timeout(const Duration(seconds: 10));
      if (_disposed) {
        unawaited(socket.close());
        return;
      }
      _socket = socket;
      _lastMessage = DateTime.now();
      socket.listen(
        _message,
        onError: (Object error) => _disconnected(socket),
        onDone: () => _disconnected(socket),
        cancelOnError: true,
      );
      _send({'type': 'auth', 'token': token});
      _heartbeat?.cancel();
      _heartbeat = Timer.periodic(const Duration(seconds: 20), (_) {
        if (DateTime.now().difference(_lastMessage).inSeconds > 60 || !_ready) {
          unawaited(socket.close());
          _disconnected(socket);
        } else {
          _send({'type': 'ping'});
        }
      });
    } on Object catch (error) {
      if (error is ApiException && error.statusCode == 401) {
        _fatal = error;
        for (final topic in _topics.values) {
          topic.addError(error);
        }
      }
      _scheduleReconnect();
    } finally {
      _connecting = false;
    }
  }

  void _message(dynamic raw) {
    if (_disposed) return;
    try {
      final frame = jsonDecode(raw as String) as Map<String, dynamic>;
      _lastMessage = DateTime.now();
      final type = frame['type'];
      if (type == 'ready') {
        _ready = true;
        _failures = 0;
        for (final key in _topics.keys) {
          final colon = key.indexOf(':');
          _send({
            'type': 'subscribe',
            'topic': key.substring(0, colon),
            'orderNo': key.substring(colon + 1),
          });
        }
      } else if (type == 'subscription.closed') {
        final controller = _topics.remove(
          '${frame['topic']}:${frame['orderNo']}',
        );
        unawaited(controller?.close());
      } else if (type == 'snapshot' || type == 'subscription.error') {
        final controller = _topics['${frame['topic']}:${frame['orderNo']}'];
        if (type == 'snapshot') {
          controller?.add(frame['data'] as Map<String, dynamic>);
        } else {
          // Missing first position is transient: keep subscription for the next fix.
          if (frame['transient'] != true) {
            controller?.addError(
              ApiException(
                frame['message'] as String? ?? '实时数据暂不可用',
                statusCode: frame['code'] as int?,
              ),
            );
          }
        }
      } else if (type == 'ack') {
        final pending = _pending[frame['id']];
        if (pending != null && !pending.isCompleted) pending.complete();
      } else if (type == 'error') {
        if (frame['code'] == 401) _refresh = true;
        if (frame['code'] == 403) {
          _fatal = const ApiException('当前账号不可用，请重新登录。', statusCode: 403);
        }
        final pending = _pending[frame['id']];
        if (pending != null && !pending.isCompleted) {
          pending.completeError(
            ApiException(
              frame['message'] as String? ?? '实时请求失败',
              statusCode: frame['code'] as int?,
            ),
          );
        }
      }
      _events.add(frame);
    } on Object {
      unawaited(_socket?.close());
    }
  }

  void _send(Map<String, dynamic> frame) {
    _socket?.add(jsonEncode(frame));
  }

  void _disconnected(WebSocket socket) {
    if (!identical(_socket, socket)) return;
    if (socket.closeCode == 1008) _refresh = true;
    _socket = null;
    _ready = false;
    _heartbeat?.cancel();
    if (socket.closeCode == 1003) {
      _fatal = const ApiException('当前账号不可用，请重新登录。', statusCode: 403);
      for (final topic in _topics.values) {
        topic.addError(_fatal!);
      }
    }
    for (final pending in _pending.values) {
      if (!pending.isCompleted) {
        pending.completeError(const ApiException('实时连接已断开。'));
      }
    }
    _scheduleReconnect();
  }

  void _scheduleReconnect() {
    if (_disposed || _fatal != null) return;
    _retry?.cancel();
    _retry = Timer(
      Duration(seconds: (1 << (_failures++).clamp(0, 5)).clamp(1, 30)),
      start,
    );
  }

  Future<void> dispose() async {
    _disposed = true;
    _retry?.cancel();
    _heartbeat?.cancel();
    await _socket?.close();
    for (final pending in _pending.values) {
      if (!pending.isCompleted) {
        pending.completeError(const ApiException('实时连接已关闭。', cancelled: true));
      }
    }
    for (final topic in _topics.values.toList()) {
      unawaited(topic.close());
    }
    _topics.clear();
    await _events.close();
  }
}
