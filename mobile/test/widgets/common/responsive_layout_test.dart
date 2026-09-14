import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/widgets/common/responsive_layout.dart';

void main() {
  final target = find.byKey(const ValueKey('content'));

  Future<void> pumpAt(WidgetTester tester, Size size, Widget child) async {
    tester.view.devicePixelRatio = 1.0;
    tester.view.physicalSize = size;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(MaterialApp(home: Scaffold(body: child)));
  }

  testWidgets('telefonda butun kenglikni egallaydi', (tester) async {
    await pumpAt(
      tester,
      const Size(390, 844),
      const ResponsiveCenter(
        child: SizedBox.expand(
          key: ValueKey('content'),
          child: ColoredBox(color: Colors.white),
        ),
      ),
    );

    expect(tester.getSize(target).width, 390);
  });

  testWidgets('planshetda kenglik cheklanadi va markazlashadi', (tester) async {
    await pumpAt(
      tester,
      const Size(1200, 900),
      const ResponsiveCenter(
        child: SizedBox.expand(
          key: ValueKey('content'),
          child: ColoredBox(color: Colors.white),
        ),
      ),
    );

    // Katta ekran => contentMaxWidth 1040, gorizontal markazda.
    expect(tester.getSize(target).width, 1040);
    expect(tester.getTopLeft(target).dx, 80);
  });

  testWidgets('wide varianti kengroq chegara beradi', (tester) async {
    await pumpAt(
      tester,
      const Size(1000, 900),
      const ResponsiveCenter.wide(
        child: SizedBox.expand(
          key: ValueKey('content'),
          child: ColoredBox(color: Colors.white),
        ),
      ),
    );

    expect(tester.getSize(target).width, 1000);
  });

  testWidgets('aniq maxWidth berilganda unga bo\'ysunadi', (tester) async {
    await pumpAt(
      tester,
      const Size(1200, 900),
      const ResponsiveCenter(
        maxWidth: 600,
        child: SizedBox.expand(
          key: ValueKey('content'),
          child: ColoredBox(color: Colors.white),
        ),
      ),
    );

    expect(tester.getSize(target).width, 600);
    expect(tester.getTopLeft(target).dx, 300);
  });
}
