import 'package:flutter/material.dart';

import '../../core/utils/responsive.dart';

/// Navigatsiya nuqtasi ta'rifi — pastki panel va yon panel uchun umumiy.
class AdaptiveNavDestination {
  final IconData icon;
  final IconData selectedIcon;
  final String label;

  const AdaptiveNavDestination({
    required this.icon,
    required this.selectedIcon,
    required this.label,
  });
}

/// Ekran kengligiga qarab navigatsiyani almashtiradigan qobiq (shell):
/// - telefon (< 600dp): pastki `NavigationBar`
/// - planshet (>= 600dp): yon `NavigationRail`
/// - katta ekran (>= 1200dp): yorliqlari ko'rinadigan yoyilgan `NavigationRail`
class AdaptiveNavScaffold extends StatelessWidget {
  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;
  final List<AdaptiveNavDestination> destinations;
  final Widget child;

  const AdaptiveNavScaffold({
    super.key,
    required this.selectedIndex,
    required this.onDestinationSelected,
    required this.destinations,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    if (!context.useNavigationRail) {
      return Scaffold(
        body: child,
        bottomNavigationBar: NavigationBar(
          selectedIndex: selectedIndex,
          onDestinationSelected: onDestinationSelected,
          destinations: [
            for (final d in destinations)
              NavigationDestination(
                icon: Icon(d.icon),
                selectedIcon: Icon(d.selectedIcon),
                label: d.label,
              ),
          ],
        ),
      );
    }

    return Scaffold(
      body: Row(
        children: [
          _AdaptiveRail(
            selectedIndex: selectedIndex,
            onDestinationSelected: onDestinationSelected,
            destinations: destinations,
          ),
          const VerticalDivider(width: 1, thickness: 1),
          Expanded(child: child),
        ],
      ),
    );
  }
}

class _AdaptiveRail extends StatelessWidget {
  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;
  final List<AdaptiveNavDestination> destinations;

  const _AdaptiveRail({
    required this.selectedIndex,
    required this.onDestinationSelected,
    required this.destinations,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final extended = context.useExtendedRail;

    // Rail past ekranlarda (masalan landshaft) sig'masligi mumkin —
    // shuning uchun aylantiriladigan qilib o'raymiz.
    return LayoutBuilder(
      builder: (context, constraints) {
        return SingleChildScrollView(
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: constraints.maxHeight),
            child: IntrinsicHeight(
              child: NavigationRail(
                backgroundColor: theme.navigationBarTheme.backgroundColor,
                selectedIndex: selectedIndex,
                onDestinationSelected: onDestinationSelected,
                extended: extended,
                labelType: extended
                    ? NavigationRailLabelType.none
                    : NavigationRailLabelType.all,
                minWidth: 76,
                minExtendedWidth: 208,
                groupAlignment: -0.85,
                indicatorColor: theme.colorScheme.primary.withValues(
                  alpha: 0.12,
                ),
                selectedIconTheme: IconThemeData(
                  color: theme.colorScheme.primary,
                ),
                unselectedIconTheme: IconThemeData(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                selectedLabelTextStyle: theme.textTheme.labelMedium?.copyWith(
                  color: theme.colorScheme.primary,
                  fontWeight: FontWeight.w600,
                ),
                unselectedLabelTextStyle: theme.textTheme.labelMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                destinations: [
                  for (final d in destinations)
                    NavigationRailDestination(
                      icon: Icon(d.icon),
                      selectedIcon: Icon(d.selectedIcon),
                      label: Text(d.label),
                    ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}
