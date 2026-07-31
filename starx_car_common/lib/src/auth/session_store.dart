import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'auth_session.dart';

/// Encrypted device storage for the role-neutral authenticated session.
class SessionStore {
  SessionStore({FlutterSecureStorage? storage})
    : _storage = storage ?? const FlutterSecureStorage();

  static const _key = 'starx_car.auth_session';
  final FlutterSecureStorage _storage;

  Future<AuthSession?> read() async {
    final value = await _storage.read(key: _key);
    if (value == null) return null;
    try {
      return AuthSession.fromJson(jsonDecode(value) as Map<String, dynamic>);
    } on FormatException {
      await clear();
      return null;
    } on TypeError {
      await clear();
      return null;
    }
  }

  Future<void> save(AuthSession session) =>
      _storage.write(key: _key, value: jsonEncode(session.toJson()));

  Future<void> clear() => _storage.delete(key: _key);
}
