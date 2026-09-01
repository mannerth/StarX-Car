import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'features/auth/auth_controller.dart';
import 'features/auth/auth_page.dart';
import 'features/profile/profile_page.dart';
import 'features/orders/passenger_orders_page.dart';
import 'features/booking/booking_page.dart';

class PassengerApp extends ConsumerWidget {
  const PassengerApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp(
      title: 'StarX-Car 乘客端',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF0B7A5C),
          brightness: Brightness.light,
        ),
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xFFF7F9F8),
        appBarTheme: const AppBarTheme(centerTitle: false, elevation: 0),
        cardTheme: const CardThemeData(
          elevation: 0,
          margin: EdgeInsets.zero,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(16))),
        ),
        inputDecorationTheme: const InputDecorationTheme(
          filled: true,
          fillColor: Colors.white,
          border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(12)), borderSide: BorderSide.none),
        ),
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(50), shape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(14)))),
        ),
        navigationBarTheme: const NavigationBarThemeData(height: 68, labelBehavior: NavigationDestinationLabelBehavior.alwaysShow),
      ),
      home: const _SessionGate(),
    );
  }
}

class _SessionGate extends ConsumerWidget {
  const _SessionGate();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ref
        .watch(passengerAuthProvider)
        .when(
          loading: () =>
              const Scaffold(body: Center(child: CircularProgressIndicator())),
          error: (error, stackTrace) => const PassengerAuthPage(),
          data: (session) => session == null
              ? const PassengerAuthPage()
              : const PassengerHomePage(),
        );
  }
}

class PassengerHomePage extends StatefulWidget {
  const PassengerHomePage({super.key});

  @override
  State<PassengerHomePage> createState() => _PassengerHomePageState();
}

class _PassengerHomePageState extends State<PassengerHomePage> {
  int _index = 0;

  @override
  Widget build(BuildContext context) => Scaffold(
    body: IndexedStack(
      index: _index,
      children: const [
        PassengerBookingPage(),
        PassengerOrdersPage(),
        PassengerProfilePage(),
      ],
    ),
    bottomNavigationBar: NavigationBar(
      selectedIndex: _index,
      onDestinationSelected: (value) => setState(() => _index = value),
      destinations: const [
        NavigationDestination(
          icon: Icon(Icons.local_taxi_outlined),
          selectedIcon: Icon(Icons.local_taxi),
          label: '叫车',
        ),
        NavigationDestination(
          icon: Icon(Icons.route_outlined),
          selectedIcon: Icon(Icons.route),
          label: '行程',
        ),
        NavigationDestination(
          icon: Icon(Icons.person_outline),
          selectedIcon: Icon(Icons.person),
          label: '我的',
        ),
      ],
    ),
  );
}
