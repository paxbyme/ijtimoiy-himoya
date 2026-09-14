import 'package:flutter/material.dart';

import '../../core/utils/responsive.dart';

/// Kontentni keng ekranlarda markazlashtiradi va maksimal kenglik bilan
/// cheklaydi. Telefonda hech narsani o'zgartirmaydi (maksimal kenglik cheksiz),
/// planshet/desktopda esa satrlar cho'zilib ketishining oldini oladi.
class ResponsiveCenter extends StatelessWidget {
  /// Maksimal kenglik. Berilmasa ekran sinfiga mos qiymat olinadi.
  final double? maxWidth;

  /// Kenglik cheklovidan keyin qo'llanadigan ichki bo'sh joy.
  final EdgeInsetsGeometry? padding;

  final AlignmentGeometry alignment;

  /// `true` bo'lsa grid/dashboard uchun kengroq chegara ishlatiladi.
  final bool wide;

  final Widget child;

  const ResponsiveCenter({
    super.key,
    this.maxWidth,
    this.padding,
    this.alignment = Alignment.topCenter,
    required this.child,
  }) : wide = false;

  /// Grid va dashboard kabi keng kontent uchun.
  const ResponsiveCenter.wide({
    super.key,
    this.maxWidth,
    this.padding,
    this.alignment = Alignment.topCenter,
    required this.child,
  }) : wide = true;

  @override
  Widget build(BuildContext context) {
    final limit =
        maxWidth ??
        (wide ? context.wideContentMaxWidth : context.contentMaxWidth);

    Widget content = child;
    if (padding != null) {
      content = Padding(padding: padding!, child: content);
    }

    return Align(
      alignment: alignment,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: limit),
        child: content,
      ),
    );
  }
}

/// Ekran o'lchami sinfiga qarab turli widget quradigan yordamchi.
class ResponsiveBuilder extends StatelessWidget {
  final Widget Function(BuildContext context, WindowSize size) builder;

  const ResponsiveBuilder({super.key, required this.builder});

  @override
  Widget build(BuildContext context) => builder(context, context.windowSize);
}
