import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';
import '../../core/providers.dart';
import '../auth/auth_controller.dart';
import 'driver_repository.dart';

final driverRepositoryProvider = Provider<DriverRepository>(
  (ref) => DriverRepository(ref.read(apiClientProvider)),
);
final userProfileProvider = FutureProvider<UserProfile>(
  (ref) => ref.read(driverRepositoryProvider).getUser(),
);
final driverProfileProvider = FutureProvider<Map<String, dynamic>>(
  (ref) => ref.read(driverRepositoryProvider).getDriver(),
);
final carProvider = FutureProvider<Map<String, dynamic>?>(
  (ref) => ref.read(driverRepositoryProvider).getCar(),
);

class DriverHomePage extends ConsumerWidget {
  const DriverHomePage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => DefaultTabController(
    length: 2,
    child: Scaffold(
      appBar: AppBar(
        title: const Text('司机工作台'),
        actions: [
          IconButton(
            onPressed: () => ref.read(driverAuthProvider.notifier).logout(),
            icon: const Icon(Icons.logout),
          ),
        ],
        bottom: const TabBar(
          tabs: [
            Tab(text: '个人中心'),
            Tab(text: '我的车辆'),
          ],
        ),
      ),
      body: const TabBarView(children: [DriverProfileTab(), CarTab()]),
    ),
  );
}

class DriverProfileTab extends ConsumerWidget {
  const DriverProfileTab({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(userProfileProvider);
    final driver = ref.watch(driverProfileProvider);
    if (user.isLoading || driver.isLoading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (user.hasError || driver.hasError) {
      return Center(child: Text('加载失败：${user.error ?? driver.error}'));
    }
    final u = user.requireValue;
    final d = driver.requireValue;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(u.username, style: Theme.of(context).textTheme.headlineSmall),
        Card(
          child: Column(
            children: [
              ListTile(title: const Text('用户名'), subtitle: Text(u.username)),
              ListTile(
                title: const Text('邮箱'),
                subtitle: Text(u.email ?? '未填写'),
              ),
              ListTile(
                title: const Text('性别'),
                subtitle: Text(u.gender == 'male' ? '男' : '女'),
              ),
              ListTile(
                title: const Text('手机号'),
                subtitle: Text(
                  d['displayPhone'] as String? ?? d['phoneNumber'] as String,
                ),
              ),
              ListTile(
                title: const Text('驾驶证号'),
                subtitle: Text(d['licenseNumber'] as String),
              ),
            ],
          ),
        ),
        FilledButton.tonal(
          onPressed: () => _editBaseProfile(context, ref, u),
          child: const Text('修改基础资料'),
        ),
        FilledButton.tonal(
          onPressed: () => _editDriver(context, ref, d),
          child: const Text('修改司机资料'),
        ),
        OutlinedButton(
          onPressed: () => _changePassword(context, ref),
          child: const Text('修改密码'),
        ),
      ],
    );
  }

  Future<void> _editBaseProfile(
    BuildContext context,
    WidgetRef ref,
    UserProfile user,
  ) async {
    await _showDriverFormDialog(
      context,
      title: '修改基础资料',
      initialValues: [user.username, user.email ?? '', user.gender],
      labels: const ['用户名', '邮箱', '性别（male 或 female）'],
      onSave: (values) async {
        if (values[2] != 'male' && values[2] != 'female') {
          throw const ApiException('性别只能填写 male 或 female。');
        }
        await ref.read(driverRepositoryProvider).updateUser({
          'username': values[0].trim(),
          'email': values[1].trim(),
          'gender': values[2],
        });
        ref.invalidate(userProfileProvider);
      },
    );
  }

  Future<void> _editDriver(
    BuildContext context,
    WidgetRef ref,
    Map<String, dynamic> driver,
  ) async {
    await _showDriverFormDialog(
      context,
      title: '修改司机资料',
      initialValues: [
        driver['phoneNumber'] as String,
        driver['licenseNumber'] as String,
      ],
      labels: const ['手机号', '驾驶证号'],
      onSave: (values) async {
        await ref.read(driverRepositoryProvider).updateDriver({
          'phoneNumber': values[0].trim(),
          'licenseNumber': values[1].trim(),
        });
        ref.invalidate(driverProfileProvider);
      },
    );
  }

  Future<void> _changePassword(BuildContext context, WidgetRef ref) async {
    await _showDriverFormDialog(
      context,
      title: '修改密码',
      initialValues: const ['', ''],
      labels: const ['原密码', '新密码（至少 6 位）'],
      obscureText: true,
      onSave: (values) async {
        if (values[1].length < 6) throw const ApiException('新密码至少 6 位');
        await ref
            .read(driverRepositoryProvider)
            .changePassword(values[0], values[1]);
      },
    );
  }
}

class CarTab extends ConsumerWidget {
  const CarTab({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final car = ref.watch(carProvider);
    if (car.isLoading) return const Center(child: CircularProgressIndicator());
    if (car.hasError) return Center(child: Text('加载失败：${car.error}'));
    final value = car.requireValue;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (value == null)
          const Text('尚未绑定车辆，创建后会自动绑定。')
        else
          Card(
            child: Column(
              children: [
                ListTile(
                  title: const Text('车牌号'),
                  subtitle: Text(value['licenseNumber'] as String),
                ),
                ListTile(
                  title: const Text('品牌'),
                  subtitle: Text(value['brand'] as String),
                ),
                ListTile(
                  title: const Text('车型'),
                  subtitle: Text(value['carType'] as String),
                ),
              ],
            ),
          ),
        const SizedBox(height: 12),
        FilledButton.icon(
          onPressed: () => _editCar(context, ref, value),
          icon: const Icon(Icons.directions_car),
          label: Text(value == null ? '创建并绑定车辆' : '修改绑定车辆'),
        ),
      ],
    );
  }

  Future<void> _editCar(
    BuildContext context,
    WidgetRef ref,
    Map<String, dynamic>? car,
  ) async {
    await _showDriverFormDialog(
      context,
      title: car == null ? '创建并绑定车辆' : '修改绑定车辆',
      initialValues: [
        car?['licenseNumber'] as String? ?? '',
        car?['carNumber'] as String? ?? '',
        car?['carType'] as String? ?? '',
        car?['brand'] as String? ?? '',
        car?['color'] as String? ?? '',
      ],
      labels: const ['车牌号', '车辆编号', '车型', '品牌', '颜色'],
      onSave: (values) async {
        await ref.read(driverRepositoryProvider).saveCar({
          'licenseNumber': values[0].trim(),
          'carNumber': values[1].trim(),
          'carType': values[2].trim(),
          'brand': values[3].trim(),
          'color': values[4].trim(),
        }, (car?['id'] as num?)?.toInt());
        ref.invalidate(carProvider);
      },
    );
  }
}

Future<void> _showDriverFormDialog(
  BuildContext context, {
  required String title,
  required List<String> initialValues,
  required List<String> labels,
  required Future<void> Function(List<String>) onSave,
  bool obscureText = false,
}) => showDialog<void>(
  context: context,
  builder: (_) => _DriverFormDialog(
    title: title,
    initialValues: initialValues,
    labels: labels,
    onSave: onSave,
    obscureText: obscureText,
  ),
);

class _DriverFormDialog extends StatefulWidget {
  const _DriverFormDialog({
    required this.title,
    required this.initialValues,
    required this.labels,
    required this.onSave,
    required this.obscureText,
  });
  final String title;
  final List<String> initialValues;
  final List<String> labels;
  final Future<void> Function(List<String>) onSave;
  final bool obscureText;
  @override
  State<_DriverFormDialog> createState() => _DriverFormDialogState();
}

class _DriverFormDialogState extends State<_DriverFormDialog> {
  late final List<TextEditingController> _controllers;
  bool _saving = false;
  @override
  void initState() {
    super.initState();
    _controllers = widget.initialValues
        .map((value) => TextEditingController(text: value))
        .toList();
  }

  @override
  void dispose() {
    for (final controller in _controllers) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      await widget.onSave(
        _controllers.map((controller) => controller.text).toList(),
      );
      if (mounted) {
        Navigator.pop(context);
      }
    } on ApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.title),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: List.generate(
          _controllers.length,
          (index) => Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: TextField(
              controller: _controllers[index],
              obscureText: widget.obscureText,
              decoration: InputDecoration(labelText: widget.labels[index]),
            ),
          ),
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: _saving ? null : () => Navigator.pop(context),
        child: const Text('取消'),
      ),
      FilledButton(
        onPressed: _saving ? null : _save,
        child: Text(_saving ? '保存中…' : '保存'),
      ),
    ],
  );
}
