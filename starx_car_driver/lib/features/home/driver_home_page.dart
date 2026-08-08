import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';
import 'package:image_picker/image_picker.dart';
import '../auth/auth_controller.dart';
import 'driver_repository.dart';
import '../work/driver_work_page.dart';

const _plateProvinces = <String>[
  '京',
  '津',
  '冀',
  '晋',
  '蒙',
  '辽',
  '吉',
  '黑',
  '沪',
  '苏',
  '浙',
  '皖',
  '闽',
  '赣',
  '鲁',
  '豫',
  '鄂',
  '湘',
  '粤',
  '桂',
  '琼',
  '渝',
  '川',
  '贵',
  '云',
  '藏',
  '陕',
  '甘',
  '青',
  '宁',
  '新',
];

const _plateLetters = <String>[
  'A',
  'B',
  'C',
  'D',
  'E',
  'F',
  'G',
  'H',
  'J',
  'K',
  'L',
  'M',
  'N',
  'P',
  'Q',
  'R',
  'S',
  'T',
  'U',
  'V',
  'W',
  'X',
  'Y',
  'Z',
];

const _carColors = <String>[
  '白色',
  '黑色',
  '银色',
  '灰色',
  '红色',
  '金色',
  '蓝色',
  '棕色',
  '紫色',
  '绿色',
  '黄色',
  '粉色',
  '橙色',
];

final userProfileProvider = FutureProvider<UserProfile>((ref) {
  // Recreate data providers whenever a different driver session is restored
  // or established; otherwise Riverpod can retain a previous driver's data.
  ref.watch(driverAuthProvider);
  return ref.read(driverRepositoryProvider).getUser();
});
final driverProfileProvider = FutureProvider<Map<String, dynamic>>((ref) {
  ref.watch(driverAuthProvider);
  return ref.read(driverRepositoryProvider).getDriver();
});
final carsProvider = FutureProvider<List<Map<String, dynamic>>>((ref) {
  ref.watch(driverAuthProvider);
  return ref.read(driverRepositoryProvider).getCars();
});

class DriverHomePage extends ConsumerWidget {
  const DriverHomePage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => DefaultTabController(
    length: 3,
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
            Tab(text: '听单'),
            Tab(text: '个人中心'),
            Tab(text: '我的车辆'),
          ],
        ),
      ),
      body: const TabBarView(
        children: [DriverWorkPage(), DriverProfileTab(), CarTab()],
      ),
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
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('司机资料加载失败'),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: () {
                ref.invalidate(userProfileProvider);
                ref.invalidate(driverProfileProvider);
              },
              icon: const Icon(Icons.refresh),
              label: const Text('重试'),
            ),
          ],
        ),
      );
    }
    final u = user.requireValue;
    final d = driver.requireValue;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Center(
          child: CircleAvatar(
            radius: 34,
            backgroundImage: u.avatarUrl?.isNotEmpty == true
                ? NetworkImage(u.avatarUrl!)
                : null,
            child: Text((u.realName ?? u.username).substring(0, 1)),
          ),
        ),
        const SizedBox(height: 12),
        Text(
          u.realName ?? u.username,
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        Card(
          child: Column(
            children: [
              ListTile(title: const Text('用户名'), subtitle: Text(u.username)),
              ListTile(
                title: const Text('真实姓名'),
                subtitle: Text(u.realName ?? '未填写'),
              ),
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
              ListTile(
                title: const Text('身份证号'),
                subtitle: Text(d['idCardNo'] as String? ?? '未填写'),
              ),
              ListTile(
                title: const Text('驾驶证有效期'),
                subtitle: Text(d['licenseExpireDate'] as String? ?? '未填写'),
              ),
              ListTile(
                title: const Text('审核状态'),
                subtitle: Text(d['approvalStatus'] as String? ?? 'pending'),
              ),
              ListTile(
                title: const Text('工作状态'),
                subtitle: Text(d['workStatus'] as String? ?? 'offline'),
              ),
            ],
          ),
        ),
        FilledButton.tonal(
          onPressed: () => _editBaseProfile(context, ref, u),
          child: const Text('修改基础资料'),
        ),
        FilledButton.tonal(
          onPressed: () => _updateAvatar(context, ref, u),
          child: const Text('修改头像'),
        ),
        FilledButton.tonal(
          onPressed: () => _editDriver(context, ref, d),
          child: const Text('修改司机资料'),
        ),
        OutlinedButton(
          onPressed: () => _changePassword(context, ref),
          child: const Text('修改密码'),
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

  Future<void> _deleteAccount(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('确认注销账号？'),
        content: const Text('注销后将无法登录，历史订单会保留，但账号不可恢复。'),
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
      await ref.read(driverRepositoryProvider).deleteAccount();
      await ref.read(driverAuthProvider.notifier).logout();
    } on ApiException catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    }
  }

  Future<void> _editBaseProfile(
    BuildContext context,
    WidgetRef ref,
    UserProfile user,
  ) async {
    await _showDriverFormDialog(
      context,
      title: '修改基础资料',
      initialValues: [
        user.username,
        user.email ?? '',
        user.gender,
        user.realName ?? '',
      ],
      labels: const ['用户名', '邮箱', '性别', '真实姓名'],
      genderIndex: 2,
      onSave: (values) async {
        await ref.read(driverRepositoryProvider).updateUser({
          'username': values[0].trim(),
          'email': values[1].trim(),
          'gender': values[2],
          'realName': values[3].trim(),
          'avatarUrl': user.avatarUrl,
        });
        ref.invalidate(userProfileProvider);
      },
    );
  }

  Future<void> _updateAvatar(
    BuildContext context,
    WidgetRef ref,
    UserProfile user,
  ) async {
    final avatar = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      imageQuality: 85,
      maxWidth: 1024,
    );
    if (avatar == null || !context.mounted) return;
    try {
      await ref.read(driverRepositoryProvider).updateAvatar(user, avatar);
      ref.invalidate(userProfileProvider);
    } on ApiException catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    }
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
        driver['idCardNo'] as String? ?? '',
        driver['licenseExpireDate'] as String? ?? '',
      ],
      labels: const ['手机号', '驾驶证号', '身份证号（可选）', '驾驶证有效期（可选）'],
      dateIndex: 3,
      onSave: (values) async {
        await ref.read(driverRepositoryProvider).updateDriver({
          'phoneNumber': values[0].trim(),
          'licenseNumber': values[1].trim(),
          'idCardNo': values[2].trim().isEmpty ? null : values[2].trim(),
          'licenseExpireDate': values[3].trim().isEmpty
              ? null
              : values[3].trim(),
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
    final driver = ref.watch(driverProfileProvider);
    final cars = ref.watch(carsProvider);
    if (driver.isLoading || cars.isLoading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (driver.hasError || cars.hasError) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('车辆资料加载失败'),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: () {
                ref.invalidate(driverProfileProvider);
                ref.invalidate(carsProvider);
              },
              icon: const Icon(Icons.refresh),
              label: const Text('重试'),
            ),
          ],
        ),
      );
    }
    final currentCarId = (driver.requireValue['carId'] as num?)?.toInt();
    final values = cars.requireValue;
    final currentCar = values.cast<Map<String, dynamic>?>().firstWhere(
      (car) => (car?['id'] as num?)?.toInt() == currentCarId,
      orElse: () => null,
    );
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (values.isEmpty) const Text('尚未绑定车辆，创建后会自动绑定。'),
        for (final car in values) _carCard(context, ref, car, currentCarId),
        const SizedBox(height: 12),
        FilledButton.icon(
          onPressed: () => _editCar(context, ref, currentCar),
          icon: const Icon(Icons.directions_car),
          label: Text(currentCar == null ? '创建并绑定车辆' : '修改当前车辆'),
        ),
      ],
    );
  }

  Widget _carCard(
    BuildContext context,
    WidgetRef ref,
    Map<String, dynamic> car,
    int? currentCarId,
  ) {
    final carId = (car['id'] as num?)?.toInt();
    final isCurrent = carId != null && carId == currentCarId;
    final plate = [
      car['plateProvince'],
      car['plateLetter'],
      car['plateSerial'],
    ].whereType<String>().join();
    return Card(
      child: Column(
        children: [
          ListTile(
            title: Text(plate.isEmpty ? '未填写车牌' : plate),
            subtitle: Text(
              '${car['brand'] ?? '未知品牌'} ${car['carType'] ?? '未知车型'}',
            ),
            trailing: isCurrent
                ? const Chip(label: Text('当前使用'))
                : OutlinedButton(
                    onPressed: carId == null
                        ? null
                        : () => _switchCurrentCar(context, ref, carId),
                    child: const Text('切换'),
                  ),
          ),
          ListTile(
            dense: true,
            title: const Text('登记归属地'),
            subtitle: Text(car['registrationPlace'] as String? ?? '未填写'),
          ),
          ListTile(
            dense: true,
            title: const Text('核定载客数'),
            subtitle: Text('${car['seatCount'] ?? '—'} 人'),
          ),
        ],
      ),
    );
  }

  Future<void> _switchCurrentCar(
    BuildContext context,
    WidgetRef ref,
    int carId,
  ) async {
    try {
      await ref.read(driverRepositoryProvider).changeCurrentCar(carId);
      ref.invalidate(driverProfileProvider);
      ref.invalidate(carsProvider);
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('当前车辆已切换')));
      }
    } on ApiException catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    }
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
        car?['plateProvince'] as String? ?? '',
        car?['plateLetter'] as String? ?? '',
        car?['plateSerial'] as String? ?? '',
        car?['registrationPlace'] as String? ?? '',
        '${car?['seatCount'] ?? 5}',
        '${car?['vehicleYear'] ?? ''}',
        car?['carType'] as String? ?? '',
        car?['brand'] as String? ?? '',
        car?['color'] as String? ?? '',
      ],
      labels: const [
        '行驶证号',
        '车牌省份简称',
        '车牌字母',
        '车牌号码部分',
        '登记归属地',
        '核定载客数',
        '出厂年份（可选）',
        '车型',
        '品牌',
        '颜色',
      ],
      yearIndex: 6,
      selectOptions: const {
        1: _plateProvinces,
        2: _plateLetters,
        9: _carColors,
      },
      onSave: (values) async {
        final seatCount = int.tryParse(values[5].trim());
        final vehicleYear = values[6].trim().isEmpty
            ? null
            : int.tryParse(values[6].trim());
        if (seatCount == null || seatCount < 1 || seatCount > 9) {
          throw const ApiException('核定载客数应为 1 到 9。');
        }
        if (!_plateProvinces.contains(values[1]) ||
            !_plateLetters.contains(values[2]) ||
            values[3].trim().isEmpty) {
          throw const ApiException('请选择规范的车牌省份和字母，并填写车牌号码部分。');
        }
        if (!_carColors.contains(values[9])) {
          throw const ApiException('请选择车辆颜色。');
        }
        if (values[6].trim().isNotEmpty && vehicleYear == null) {
          throw const ApiException('出厂年份必须是数字。');
        }
        final carData = <String, Object>{
          'licenseNumber': values[0].trim(),
          'plateProvince': values[1].trim(),
          'plateLetter': values[2].trim(),
          'plateSerial': values[3].trim(),
          'registrationPlace': values[4].trim(),
          'seatCount': seatCount,
          'carType': values[7].trim(),
          'brand': values[8].trim(),
          'color': values[9].trim(),
        };
        if (vehicleYear != null) {
          carData['vehicleYear'] = vehicleYear;
        }
        await ref
            .read(driverRepositoryProvider)
            .saveCar(carData, (car?['id'] as num?)?.toInt());
        ref.invalidate(driverProfileProvider);
        ref.invalidate(carsProvider);
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
  int? genderIndex,
  int? dateIndex,
  int? yearIndex,
  Map<int, List<String>> selectOptions = const {},
}) => showDialog<void>(
  context: context,
  builder: (_) => _DriverFormDialog(
    title: title,
    initialValues: initialValues,
    labels: labels,
    onSave: onSave,
    obscureText: obscureText,
    genderIndex: genderIndex,
    dateIndex: dateIndex,
    yearIndex: yearIndex,
    selectOptions: selectOptions,
  ),
);

class _DriverFormDialog extends StatefulWidget {
  const _DriverFormDialog({
    required this.title,
    required this.initialValues,
    required this.labels,
    required this.onSave,
    required this.obscureText,
    required this.genderIndex,
    required this.dateIndex,
    required this.yearIndex,
    required this.selectOptions,
  });
  final String title;
  final List<String> initialValues;
  final List<String> labels;
  final Future<void> Function(List<String>) onSave;
  final bool obscureText;
  final int? genderIndex;
  final int? dateIndex;
  final int? yearIndex;
  final Map<int, List<String>> selectOptions;
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
        children: List.generate(_controllers.length, _buildField),
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

  Widget _buildField(int index) {
    final controller = _controllers[index];
    final decoration = InputDecoration(labelText: widget.labels[index]);
    if (widget.genderIndex == index) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: DropdownButtonFormField<String>(
          initialValue: controller.text == 'female' ? 'female' : 'male',
          decoration: decoration,
          items: const [
            DropdownMenuItem(value: 'male', child: Text('男')),
            DropdownMenuItem(value: 'female', child: Text('女')),
          ],
          onChanged: (value) => controller.text = value ?? 'male',
        ),
      );
    }
    if (widget.dateIndex == index) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: TextField(
          controller: controller,
          readOnly: true,
          decoration: decoration.copyWith(
            hintText: '请选择日期',
            suffixIcon: const Icon(Icons.calendar_today),
          ),
          onTap: () async {
            final initial =
                DateTime.tryParse(controller.text) ?? DateTime.now();
            final selected = await showDatePicker(
              context: context,
              initialDate: initial,
              firstDate: DateTime(2000),
              lastDate: DateTime(2100),
            );
            if (selected != null && mounted) {
              setState(
                () => controller.text = selected
                    .toIso8601String()
                    .split('T')
                    .first,
              );
            }
          },
        ),
      );
    }
    if (widget.yearIndex == index) {
      final currentYear = DateTime.now().year + 1;
      return Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: DropdownButtonFormField<String>(
          initialValue: controller.text.isEmpty ? '' : controller.text,
          decoration: decoration,
          items: [
            const DropdownMenuItem(value: '', child: Text('未填写')),
            for (var year = currentYear; year >= 1990; year--)
              DropdownMenuItem(value: '$year', child: Text('$year 年')),
          ],
          onChanged: (value) => controller.text = value ?? '',
        ),
      );
    }
    final options = widget.selectOptions[index];
    if (options != null) {
      final selected = options.contains(controller.text) ? controller.text : '';
      return Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: DropdownButtonFormField<String>(
          initialValue: selected,
          decoration: decoration,
          items: [
            const DropdownMenuItem(value: '', child: Text('请选择')),
            ...options.map(
              (option) => DropdownMenuItem(value: option, child: Text(option)),
            ),
          ],
          onChanged: (value) => controller.text = value ?? '',
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: TextField(
        controller: controller,
        obscureText: widget.obscureText,
        decoration: decoration,
      ),
    );
  }
}
