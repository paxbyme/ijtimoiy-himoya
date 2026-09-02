import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/widgets/common/mobile_app_frame.dart';

void main() {
  final viewportFinder = find.byKey(const ValueKey('mobile-app-viewport'));

  testWidgets('limits wide screens to the mobile width and centers the app', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      const MaterialApp(
        home: MobileAppFrame(child: ColoredBox(color: Colors.white)),
      ),
    );

    expect(tester.getSize(viewportFinder), const Size(650, 800));
    expect(tester.getTopLeft(viewportFinder), const Offset(275, 0));
  });

  testWidgets('uses the full width on phone-sized screens', (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      const MaterialApp(
        home: MobileAppFrame(child: ColoredBox(color: Colors.white)),
      ),
    );

    expect(tester.getSize(viewportFinder), const Size(390, 844));
    expect(tester.getTopLeft(viewportFinder), Offset.zero);
  });
}
