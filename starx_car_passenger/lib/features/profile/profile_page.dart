import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/providers.dart';
import '../auth/auth_controller.dart';
import 'passenger_repository.dart';
import '../orders/passenger_ratings_page.dart';

final passengerRepositoryProvider = Provider<PassengerRepository>(
  (ref) => PassengerRepository(ref.read(apiClientProvider)),
);
final passengerProfileProvider = FutureProvider<UserProfile>((ref) {
  // Tie cached profile data to the active session. Without this dependency,
  // logging out and then in as another passenger can show the prior account.
  ref.watch(passengerAuthProvider);
  return ref.read(passengerRepositoryProvider).getProfile();
});
final passengerDetailProvider = FutureProvider<Map<String, dynamic>>((ref) {
  ref.watch(passengerAuthProvider);
  return ref.read(passengerRepositoryProvider).getPassengerProfile();
});
final passengerWalletProvider = FutureProvider<WalletBalance>((ref) {
  ref.watch(passengerAuthProvider);
  return ref.read(passengerRepositoryProvider).walletBalance();
});

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
        error: (error, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('个人资料加载失败'),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () {
                  ref.invalidate(passengerProfileProvider);
                  ref.invalidate(passengerDetailProvider);
                },
                icon: const Icon(Icons.refresh),
                label: const Text('重试'),
              ),
            ],
          ),
        ),
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
    final wallet = ref.watch(passengerWalletProvider);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        CircleAvatar(
          radius: 34,
          backgroundImage: profile.avatarUrl?.isNotEmpty == true
              ? NetworkImage(profile.avatarUrl!)
              : null,
          child: Text((profile.realName ?? profile.username).substring(0, 1)),
        ),
        const SizedBox(height: 12),
        InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: () => _recharge(context, ref),
          child: Ink(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(color: Theme.of(context).colorScheme.primary, borderRadius: BorderRadius.circular(16)),
            child: Row(children: [
              const Icon(Icons.account_balance_wallet_outlined, color: Colors.white, size: 30),
              const SizedBox(width: 14),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('账户余额', style: TextStyle(color: Colors.white70)),
                const SizedBox(height: 3),
                wallet.when(loading: () => const Text('加载中…', style: TextStyle(color: Colors.white)), error: (_, _) => const Text('点击重试', style: TextStyle(color: Colors.white)), data: (value) => Text('¥ ${value.balance.toStringAsFixed(2)}', style: const TextStyle(color: Colors.white, fontSize: 27, fontWeight: FontWeight.w700))),
              ])),
              const Icon(Icons.add_circle_outline, color: Colors.white),
            ]),
          ),
        ),
        const SizedBox(height: 12),
        Text(
          profile.realName ?? profile.username,
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
                title: const Text('真实姓名'),
                subtitle: Text(profile.realName ?? '未填写'),
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
                  error: (_, _) => Row(
                    children: [
                      const Text('加载失败'),
                      TextButton(
                        onPressed: () =>
                            ref.invalidate(passengerDetailProvider),
                        child: const Text('重试'),
                      ),
                    ],
                  ),
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
        Card(
          child: ListTile(
            leading: const Icon(Icons.star_outline),
            title: const Text('我的评价'),
            subtitle: const Text('查看我提交过的行程评价'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => const PassengerRatingsPage(),
              ),
            ),
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
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: () => _deleteAccount(context, ref),
          icon: const Icon(Icons.person_off),
          label: const Text('注销账号'),
          style: OutlinedButton.styleFrom(
            foregroundColor: Theme.of(context).colorScheme.error,
          ),
        ),
      ],
    );
  }

  Future<void> _recharge(BuildContext context, WidgetRef ref) async {
    final controller = TextEditingController();
    await showDialog<void>(context: context, builder: (dialogContext) => AlertDialog(title: const Text('余额充值'), content: TextField(controller: controller, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: const InputDecoration(labelText: '充值金额（元）')), actions: [TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('取消')), FilledButton(onPressed: () async { final amount = double.tryParse(controller.text.trim()); if (amount == null || amount <= 0) { ScaffoldMessenger.of(dialogContext).showSnackBar(const SnackBar(content: Text('请输入大于 0 的金额'))); return; } try { await ref.read(passengerRepositoryProvider).recharge(amount); ref.invalidate(passengerWalletProvider); if (dialogContext.mounted) Navigator.pop(dialogContext); } on ApiException catch (error) { if (dialogContext.mounted) ScaffoldMessenger.of(dialogContext).showSnackBar(SnackBar(content: Text(error.message))); } }, child: const Text('确认充值'))]));
    controller.dispose();
  }

  Future<void> _deleteAccount(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('确认注销账号？'),
        content: const Text('注销后将无法登录，历史行程会保留，但账号不可恢复。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('确认注销'),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;
    try {
      await ref.read(passengerRepositoryProvider).deleteAccount();
      await ref.read(passengerAuthProvider.notifier).logout();
    } on ApiException catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    }
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
    final realName = TextEditingController(text: profile.realName);
    XFile? avatar;
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
                controller: realName,
                decoration: const InputDecoration(labelText: '真实姓名'),
              ),
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(avatar == null ? '未选择新头像' : avatar!.name),
                trailing: OutlinedButton(
                  onPressed: () async {
                    final selected = await ImagePicker().pickImage(
                      source: ImageSource.gallery,
                      imageQuality: 85,
                      maxWidth: 1024,
                    );
                    if (selected != null) setState(() => avatar = selected);
                  },
                  child: const Text('选择图片'),
                ),
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
                      realName: realName.text.trim(),
                      avatar: avatar,
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
      realName.dispose();
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
                ScaffoldMessenger.of(
                  dialogContext,
                ).showSnackBar(const SnackBar(content: Text('新密码至少需要 6 位')));
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
