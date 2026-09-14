import 'package:flutter/material.dart';

import '../../core/utils/responsive.dart';

/// Logotipni suv belgisi sifatida ko'rsatuvchi fon.
/// Belgining o'lchami ekranga qarab moslashadi — planshetda ham,
/// telefonda ham mutanosib ko'rinadi.
class AppBackground extends StatelessWidget {
  final Widget child;

  const AppBackground({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    final size = context.watermarkSize;

    return Stack(
      children: [
        Positioned.fill(
          child: IgnorePointer(
            child: Center(
              child: Opacity(
                opacity: 0.22,
                child: Image.asset(
                  'assets/images/logo.png',
                  width: size,
                  height: size,
                  fit: BoxFit.contain,
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
