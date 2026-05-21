import 'package:flutter/material.dart';

/// Reusable background widget that shows the app logo as a watermark.
/// Used by screens that are outside the shell routes.
class AppBackground extends StatelessWidget {
  final Widget child;

  const AppBackground({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Positioned.fill(
          child: IgnorePointer(
            child: Center(
              child: Opacity(
                opacity: 0.22,
                child: Image.asset(
                  'assets/images/logo.png',
                  width: 320,
                  height: 320,
                ),
              ),
            ),
          ),
        ),
        child,
      ],
    );
  }
}
