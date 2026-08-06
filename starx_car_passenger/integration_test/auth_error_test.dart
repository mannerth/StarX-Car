import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:starx_car_passenger/main.dart' as app;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('login failure shows an error and keeps entered values', (
    tester,
  ) async {
    app.main();
    final fields = find.byType(TextFormField);
    for (var i = 0; i < 20 && fields.evaluate().length < 2; i++) {
      await tester.pump(const Duration(milliseconds: 250));
    }

    expect(fields, findsNWidgets(2));
    await tester.enterText(fields.at(0), 'alice');
    await tester.enterText(fields.at(1), 'wrong-password');
    await tester.tap(find.text('登录'));
    await tester.pump(const Duration(seconds: 2));

    expect(find.text('无法连接服务器，请检查网络和服务地址。'), findsOneWidget);
    expect(
      tester.widget<TextFormField>(fields.at(0)).controller?.text,
      'alice',
    );
    expect(
      tester.widget<TextFormField>(fields.at(1)).controller?.text,
      'wrong-password',
    );
  });
}
