import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';

import 'auth_controller.dart';

class PassengerAuthPage extends ConsumerStatefulWidget {
  const PassengerAuthPage({super.key});

  @override
  ConsumerState<PassengerAuthPage> createState() => _PassengerAuthPageState();
}

class _PassengerAuthPageState extends ConsumerState<PassengerAuthPage> {
  final _formKey = GlobalKey<FormState>();
  final _username = TextEditingController();
  final _password = TextEditingController();
  final _email = TextEditingController();
  final _phone = TextEditingController();
  final _realName = TextEditingController();
  bool _isRegistering = false;
  bool _submitting = false;
  String _gender = 'male';

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    _email.dispose();
    _phone.dispose();
    _realName.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _submitting = true);
    try {
      final controller = ref.read(passengerAuthProvider.notifier);
      if (_isRegistering) {
        await controller.register(
          username: _username.text.trim(),
          password: _password.text,
          email: _email.text.trim(),
          gender: _gender,
          phoneNumber: _phone.text.trim(),
          realName: _realName.text.trim(),
        );
      } else {
        await controller.login(
          username: _username.text.trim(),
          password: _password.text,
        );
      }
    } on ApiException catch (error) {
      if (mounted) _showError(error.message);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final isLoading = _submitting;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Icon(
                      Icons.local_taxi,
                      size: 64,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      _isRegistering ? '注册乘客账号' : '乘客登录',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 24),
                    _textField(_username, '用户名', minLength: 3),
                    _textField(
                      _password,
                      '密码',
                      obscureText: true,
                      minLength: 6,
                    ),
                    if (_isRegistering) ...[
                      _textField(_email, '邮箱', email: true),
                      _textField(_realName, '真实姓名'),
                      _textField(_phone, '手机号', phone: true),
                      DropdownButtonFormField<String>(
                        initialValue: _gender,
                        decoration: const InputDecoration(labelText: '性别'),
                        items: const [
                          DropdownMenuItem(value: 'male', child: Text('男')),
                          DropdownMenuItem(value: 'female', child: Text('女')),
                        ],
                        onChanged: (value) => setState(() => _gender = value!),
                      ),
                    ],
                    const SizedBox(height: 20),
                    FilledButton(
                      onPressed: isLoading ? null : _submit,
                      child: Text(
                        isLoading
                            ? '请稍候…'
                            : _isRegistering
                            ? '注册并登录'
                            : '登录',
                      ),
                    ),
                    TextButton(
                      onPressed: isLoading
                          ? null
                          : () => setState(
                              () => _isRegistering = !_isRegistering,
                            ),
                      child: Text(_isRegistering ? '已有账号？去登录' : '没有账号？立即注册'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _textField(
    TextEditingController controller,
    String label, {
    bool obscureText = false,
    bool email = false,
    bool phone = false,
    int minLength = 1,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextFormField(
        controller: controller,
        obscureText: obscureText,
        keyboardType: email
            ? TextInputType.emailAddress
            : phone
            ? TextInputType.phone
            : null,
        decoration: InputDecoration(
          labelText: label,
          border: const OutlineInputBorder(),
        ),
        validator: (value) {
          final text = value?.trim() ?? '';
          if (text.length < minLength) {
            return '$label至少 $minLength 位';
          }
          if (phone && !RegExp(r'^1\d{10}$').hasMatch(text)) {
            return '请输入 11 位手机号';
          }
          if (email && !text.contains('@')) {
            return '请输入有效邮箱';
          }
          return null;
        },
      ),
    );
  }
}
