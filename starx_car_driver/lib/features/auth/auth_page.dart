import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';
import 'auth_controller.dart';

class DriverAuthPage extends ConsumerStatefulWidget {
  const DriverAuthPage({super.key});
  @override
  ConsumerState<DriverAuthPage> createState() => _DriverAuthPageState();
}

class _DriverAuthPageState extends ConsumerState<DriverAuthPage> {
  final _formKey = GlobalKey<FormState>();
  final _username = TextEditingController();
  final _password = TextEditingController();
  final _email = TextEditingController();
  final _phone = TextEditingController();
  final _license = TextEditingController();
  final _realName = TextEditingController();
  bool _registering = false;
  String _gender = 'male';
  @override
  void dispose() {
    for (final controller in [
      _username,
      _password,
      _email,
      _phone,
      _license,
      _realName,
    ]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    try {
      final auth = ref.read(driverAuthProvider.notifier);
      if (_registering) {
        await auth.register({
          'username': _username.text.trim(),
          'password': _password.text,
          'email': _email.text.trim(),
          'gender': _gender,
          'phoneNumber': _phone.text.trim(),
          'licenseNumber': _license.text.trim(),
          'realName': _realName.text.trim(),
        });
      } else {
        await auth.login(_username.text.trim(), _password.text);
      }
    } on ApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final loading = ref.watch(driverAuthProvider).isLoading;
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
                      Icons.local_shipping,
                      size: 64,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      _registering ? '注册司机账号' : '司机登录',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 24),
                    _field(_username, '用户名', min: 3),
                    _field(_password, '密码', secret: true, min: 6),
                    if (_registering) ...[
                      _field(_email, '邮箱', email: true),
                      _field(_realName, '真实姓名'),
                      _field(_phone, '手机号', phone: true),
                      _field(_license, '驾驶证号'),
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
                      onPressed: loading ? null : _submit,
                      child: Text(
                        loading
                            ? '请稍候…'
                            : _registering
                            ? '注册并登录'
                            : '登录',
                      ),
                    ),
                    TextButton(
                      onPressed: loading
                          ? null
                          : () => setState(() => _registering = !_registering),
                      child: Text(_registering ? '已有账号？去登录' : '没有账号？立即注册'),
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

  Widget _field(
    TextEditingController controller,
    String label, {
    bool secret = false,
    bool email = false,
    bool phone = false,
    int min = 1,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: TextFormField(
      controller: controller,
      obscureText: secret,
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
        if (text.length < min) return '$label至少 $min 位';
        if (phone && !RegExp(r'^1\d{10}$').hasMatch(text)) return '请输入 11 位手机号';
        if (email && !text.contains('@')) return '请输入有效邮箱';
        return null;
      },
    ),
  );
}
