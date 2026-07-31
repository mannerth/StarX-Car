import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'features/auth/auth_controller.dart';
import 'features/auth/auth_page.dart';
import 'features/home/driver_home_page.dart';

class DriverApp extends ConsumerWidget {
  const DriverApp({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => MaterialApp(
    title: 'StarX-Car 司机端',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
      useMaterial3: true,
    ),
    home: ref
        .watch(driverAuthProvider)
        .when(
          loading: () =>
              const Scaffold(body: Center(child: CircularProgressIndicator())),
          error: (error, stackTrace) => const DriverAuthPage(),
          data: (session) =>
              session == null ? const DriverAuthPage() : const DriverHomePage(),
        ),
  );
}
