import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/utils/responsive.dart';

Future<BuildContext> _contextAt(WidgetTester tester, Size size) async {
  tester.view.devicePixelRatio = 1.0;
  tester.view.physicalSize = size;
  addTearDown(tester.view.reset);

  late BuildContext captured;
  await tester.pumpWidget(
    MaterialApp(
      home: Builder(
        builder: (context) {
          captured = context;
          return const SizedBox.shrink();
        },
      ),
    ),
  );
  return captured;
}

void main() {
  group('WindowSize', () {
    testWidgets('telefon kengligi compact', (tester) async {
      final context = await _contextAt(tester, const Size(390, 844));
      expect(context.windowSize, WindowSize.compact);
      expect(context.isCompact, isTrue);
      expect(context.isTabletOrWider, isFalse);
      expect(context.useNavigationRail, isFalse);
      expect(context.contentMaxWidth, double.infinity);
      expect(context.statGridColumns, 2);
    });

    testWidgets('kichik planshet medium', (tester) async {
      final context = await _contextAt(tester, const Size(700, 1000));
      expect(context.windowSize, WindowSize.medium);
      expect(context.isTabletOrWider, isTrue);
      expect(context.useNavigationRail, isTrue);
      expect(context.useExtendedRail, isFalse);
      expect(context.statGridColumns, 3);
    });

    testWidgets('planshet expanded', (tester) async {
      final context = await _contextAt(tester, const Size(1000, 1200));
      expect(context.windowSize, WindowSize.expanded);
      expect(context.useSplitView, isTrue);
      expect(context.statGridColumns, 4);
      expect(context.contentMaxWidth, 840);
    });

    testWidgets('katta ekran large va yoyilgan rail', (tester) async {
      final context = await _contextAt(tester, const Size(1400, 900));
      expect(context.windowSize, WindowSize.large);
      expect(context.useExtendedRail, isTrue);
      expect(context.pageGutter, 24);
    });
  });

  group('o\'lcham yordamchilari', () {
    testWidgets('chat pufakchasi 560 dan oshmaydi', (tester) async {
      final wide = await _contextAt(tester, const Size(1400, 900));
      expect(wide.bubbleMaxWidth, 560);

      final phone = await _contextAt(tester, const Size(400, 800));
      expect(phone.bubbleMaxWidth, closeTo(300, 0.01));
    });

    testWidgets('suv belgisi ekranga mos ravishda cheklanadi', (tester) async {
      final phone = await _contextAt(tester, const Size(360, 640));
      expect(phone.watermarkSize, closeTo(252, 0.01));

      final tablet = await _contextAt(tester, const Size(1400, 1000));
      expect(tablet.watermarkSize, closeTo(294, 0.01));
    });

    testWidgets('gridColumnsFor minimal kenglikka amal qiladi', (tester) async {
      final phone = await _contextAt(tester, const Size(390, 844));
      expect(phone.gridColumnsFor(minItemWidth: 260), 1);

      final tablet = await _contextAt(tester, const Size(1000, 1200));
      expect(tablet.gridColumnsFor(minItemWidth: 260), 3);
    });
  });
}
