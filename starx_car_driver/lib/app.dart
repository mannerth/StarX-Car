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
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF185A9D), brightness: Brightness.light),
      useMaterial3: true,
      scaffoldBackgroundColor: const Color(0xFFF6F8FB),
      appBarTheme: const AppBarTheme(centerTitle: false, elevation: 0),
      cardTheme: const CardThemeData(elevation: 0, margin: EdgeInsets.zero, shape: RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(16)))),
      inputDecorationTheme: const InputDecorationTheme(filled: true, fillColor: Colors.white, border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(12)), borderSide: BorderSide.none)),
      filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(50), shape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(14))))),
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
