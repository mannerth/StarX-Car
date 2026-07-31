import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';

import '../../core/providers.dart';
import '../auth/auth_controller.dart';
import 'passenger_repository.dart';

final passengerRepositoryProvider = Provider<PassengerRepository>(
  (ref) => PassengerRepository(ref.read(apiClientProvider)),
);
final passengerProfileProvider = FutureProvider<UserProfile>(
  (ref) => ref.read(passengerRepositoryProvider).getProfile(),
);
final passengerDetailProvider = FutureProvider<Map<String, dynamic>>(
  (ref) => ref.read(passengerRepositoryProvider).getPassengerProfile(),
);

class PassengerProfilePage extends ConsumerWidget {
  const PassengerProfilePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(passengerProfileProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('个人中心'),
        actions: [
          IconButton(
            onPressed: () => ref.invalidate(passengerProfileProvider),
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            onPressed: () => ref.read(passengerAuthProvider.notifier).logout(),
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: profile.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text('加载失败：$error')),
        data: (value) => _ProfileContent(profile: value),
      ),
    );
  }
}

class _ProfileContent extends ConsumerWidget {
  const _ProfileContent({required this.profile});
  final UserProfile profile;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final passenger = ref.watch(passengerDetailProvider);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        CircleAvatar(radius: 34, child: Text(profile.username.substring(0, 1))),
        const SizedBox(height: 12),
        Text(
          profile.username,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 20),
        Card(
          child: Column(
            children: [
              ListTile(
                title: const Text('用户名'),
                subtitle: Text(profile.username),
              ),
              ListTile(
                title: const Text('邮箱'),
                subtitle: Text(profile.email ?? '未填写'),
              ),
              ListTile(
                title: const Text('性别'),
                subtitle: Text(profile.gender == 'male' ? '男' : '女'),
              ),
              ListTile(
                title: const Text('手机号'),
                subtitle: passenger.when(
                  loading: () => const Text('加载中…'),
                  error: (_, _) => const Text('加载失败'),
                  data: (value) => Text(
                    value['displayPhone'] as String? ??
                        value['phoneNumber'] as String,
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        FilledButton.tonalIcon(
          onPressed: () => _editProfile(context, ref),
          icon: const Icon(Icons.edit),
          label: const Text('修改个人资料'),
        ),
        FilledButton.tonalIcon(
          onPressed: passenger.hasValue
              ? () => _editPhone(context, ref, passenger.requireValue)
              : null,
          icon: const Icon(Icons.phone),
          label: const Text('修改手机号'),
        ),
        OutlinedButton.icon(
          onPressed: () => _changePassword(context, ref),
          icon: const Icon(Icons.password),
          label: const Text('修改密码'),
        ),
      ],
    );
  }

  Future<void> _editPhone(
    BuildContext context,
    WidgetRef ref,
    Map<String, dynamic> passenger,
  ) async {
    final phone = TextEditingController(
      text: passenger['phoneNumber'] as String,
    );
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('修改手机号'),
        content: TextField(
          controller: phone,
          keyboardType: TextInputType.phone,
          decoration: const InputDecoration(labelText: '11 位手机号'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () async {
              final value = phone.text.trim();
              if (!RegExp(r'^1\d{10}$').hasMatch(value)) {
                ScaffoldMessenger.of(
                  dialogContext,
                ).showSnackBar(const SnackBar(content: Text('请输入 11 位手机号')));
                return;
              }
              try {
                await ref
                    .read(passengerRepositoryProvider)
                    .updatePhoneNumber(value);
                ref.invalidate(passengerDetailProvider);
                if (dialogContext.mounted) Navigator.pop(dialogContext);
              } on ApiException catch (error) {
                if (dialogContext.mounted) {
                  ScaffoldMessenger.of(
                    dialogContext,
                  ).showSnackBar(SnackBar(content: Text(error.message)));
                }
              }
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
    Future<void>.delayed(const Duration(milliseconds: 300), phone.dispose);
  }

  Future<void> _editProfile(BuildContext context, WidgetRef ref) async {
    final username = TextEditingController(text: profile.username);
    final email = TextEditingController(text: profile.email);
    String gender = profile.gender;
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('修改个人资料'),
        content: StatefulBuilder(
          builder: (context, setState) => Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: username,
                decoration: const InputDecoration(labelText: '用户名'),
              ),
              TextField(
                controller: email,
                decoration: const InputDecoration(labelText: '邮箱'),
              ),
              DropdownButtonFormField<String>(
                initialValue: gender,
                items: const [
                  DropdownMenuItem(value: 'male', child: Text('男')),
                  DropdownMenuItem(value: 'female', child: Text('女')),
                ],
                onChanged: (value) => setState(() => gender = value!),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () async {
              try {
                await ref
                    .read(passengerRepositoryProvider)
                    .updateProfile(
                      username: username.text.trim(),
                      email: email.text.trim(),
                      gender: gender,
                    );
                ref.invalidate(passengerProfileProvider);
                if (dialogContext.mounted) {
                  Navigator.pop(dialogContext);
                }
              } on ApiException catch (error) {
                if (dialogContext.mounted) {
                  ScaffoldMessenger.of(
                    dialogContext,
                  ).showSnackBar(SnackBar(content: Text(error.message)));
                }
              }
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
    // The route can still be animating out after showDialog completes. Dispose
    // after that frame sequence so a focused TextField never observes a dead
    // controller during its exit animation.
    Future<void>.delayed(const Duration(milliseconds: 300), () {
      username.dispose();
      email.dispose();
    });
  }

  Future<void> _changePassword(BuildContext context, WidgetRef ref) async {
    final current = TextEditingController();
    final fresh = TextEditingController();
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('修改密码'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: current,
              obscureText: true,
              decoration: const InputDecoration(labelText: '原密码'),
            ),
            TextField(
              controller: fresh,
              obscureText: true,
              decoration: const InputDecoration(labelText: '新密码（至少 6 位）'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () async {
              if (fresh.text.length < 6) {
                return;
              }
              try {
                await ref
                    .read(passengerRepositoryProvider)
                    .changePassword(
                      currentPassword: current.text,
                      newPassword: fresh.text,
                    );
                if (dialogContext.mounted) {
                  Navigator.pop(dialogContext);
                }
              } on ApiException catch (error) {
                if (dialogContext.mounted) {
                  ScaffoldMessenger.of(
                    dialogContext,
                  ).showSnackBar(SnackBar(content: Text(error.message)));
                }
              }
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
    Future<void>.delayed(const Duration(milliseconds: 300), () {
      current.dispose();
      fresh.dispose();
    });
  }
}
