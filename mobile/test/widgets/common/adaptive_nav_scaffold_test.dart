import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/widgets/common/adaptive_nav_scaffold.dart';

void main() {
  const destinations = [
    AdaptiveNavDestination(
      icon: Icons.home_outlined,
      selectedIcon: Icons.home,
      label: 'Bosh sahifa',
    ),
    AdaptiveNavDestination(
      icon: Icons.task_alt_outlined,
      selectedIcon: Icons.task_alt,
      label: 'Topshiriqlar',
    ),
  ];

  Future<int?> pumpAt(WidgetTester tester, Size size) async {
    int? tapped;
    tester.view.devicePixelRatio = 1.0;
    tester.view.physicalSize = size;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(
        home: AdaptiveNavScaffold(
          selectedIndex: 0,
          onDestinationSelected: (i) => tapped = i,
          destinations: destinations,
          child: const SizedBox.expand(),
        ),
      ),
    );
    return tapped;
  }

  testWidgets('telefonda pastki navigatsiya ko\'rsatiladi', (tester) async {
    await pumpAt(tester, const Size(390, 844));

    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.byType(NavigationRail), findsNothing);
  });

  testWidgets('planshetda yon navigatsiyaga o\'tadi', (tester) async {
    await pumpAt(tester, const Size(900, 1200));

    expect(find.byType(NavigationRail), findsOneWidget);
    expect(find.byType(NavigationBar), findsNothing);

    final rail = tester.widget<NavigationRail>(find.byType(NavigationRail));
    expect(rail.extended, isFalse);
    expect(rail.labelType, NavigationRailLabelType.all);
  });

  testWidgets('katta ekranda rail yoyilgan holatda', (tester) async {
    await pumpAt(tester, const Size(1400, 1000));

    final rail = tester.widget<NavigationRail>(find.byType(NavigationRail));
    expect(rail.extended, isTrue);
    expect(rail.labelType, NavigationRailLabelType.none);
  });

  testWidgets('yon navigatsiya bosilganda callback chaqiriladi', (
    tester,
  ) async {
    int? tapped;
    tester.view.devicePixelRatio = 1.0;
    tester.view.physicalSize = const Size(900, 1200);
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(
        home: AdaptiveNavScaffold(
          selectedIndex: 0,
          onDestinationSelected: (i) => tapped = i,
          destinations: destinations,
          child: const SizedBox.expand(),
        ),
      ),
    );

    await tester.tap(find.text('Topshiriqlar'));
    await tester.pumpAndSettle();

    expect(tapped, 1);
  });
}
