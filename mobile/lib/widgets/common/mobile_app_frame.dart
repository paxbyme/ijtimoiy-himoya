import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Keeps the app at a phone-sized width on tablets, desktop, and the web.
class MobileAppFrame extends StatelessWidget {
  static const double maxWidth = 650;
  static const Color backgroundColor = Color(0xFF0D1117);

  final Widget child;

  const MobileAppFrame({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);

    return ColoredBox(
      color: backgroundColor,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = math.min(constraints.maxWidth, maxWidth);

          return Center(
            child: SizedBox(
              key: const ValueKey('mobile-app-viewport'),
              width: width,
              height: constraints.maxHeight,
              child: MediaQuery(
                data: mediaQuery.copyWith(
                  size: Size(width, constraints.maxHeight),
                ),
                child: child,
              ),
            ),
          );
        },
      ),
    );
  }
}
