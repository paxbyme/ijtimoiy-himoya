import 'dart:math' as math;

import 'package:flutter/widgets.dart';

/// Material 3 oyna o'lchami sinflari (window size classes).
enum WindowSize {
  /// Telefon, portret. < 600dp
  compact,

  /// Katta telefon (landshaft), kichik planshet. 600–839dp
  medium,

  /// Planshet, kichik desktop. 840–1199dp
  expanded,

  /// Katta desktop / TV. >= 1200dp
  large,
}

/// Kenglik chegaralari — Material 3 tavsiyasiga mos.
abstract final class Breakpoints {
  static const double medium = 600;
  static const double expanded = 840;
  static const double large = 1200;
}

extension ResponsiveContext on BuildContext {
  Size get windowSizePx => MediaQuery.sizeOf(this);

  double get windowWidth => MediaQuery.sizeOf(this).width;
  double get windowHeight => MediaQuery.sizeOf(this).height;

  WindowSize get windowSize {
    final width = windowWidth;
    if (width < Breakpoints.medium) return WindowSize.compact;
    if (width < Breakpoints.expanded) return WindowSize.medium;
    if (width < Breakpoints.large) return WindowSize.expanded;
    return WindowSize.large;
  }

  bool get isCompact => windowSize == WindowSize.compact;
  bool get isMedium => windowSize == WindowSize.medium;
  bool get isExpanded => windowSize == WindowSize.expanded;
  bool get isLarge => windowSize == WindowSize.large;

  /// Planshet va undan katta ekranlar.
  bool get isTabletOrWider => windowWidth >= Breakpoints.medium;

  /// Balandligi kichik (klaviatura ochilgan yoki landshaft telefon).
  bool get isShort => windowHeight < 600;

  /// Yon navigatsiya (NavigationRail) pastki navigatsiya o'rniga ishlatiladimi.
  bool get useNavigationRail => windowWidth >= Breakpoints.medium;

  /// Yon navigatsiya yorliqlar bilan yoyilgan holdami.
  bool get useExtendedRail => windowWidth >= Breakpoints.large;

  /// Bir ustunli matn/forma kontenti uchun maksimal kenglik.
  /// Keng ekranda satrlar cho'zilib ketmasligi uchun.
  double get contentMaxWidth => switch (windowSize) {
    WindowSize.compact => double.infinity,
    WindowSize.medium => 680,
    WindowSize.expanded => 840,
    WindowSize.large => 1040,
  };

  /// Grid/dashboard kabi keng kontent uchun maksimal kenglik.
  double get wideContentMaxWidth => switch (windowSize) {
    WindowSize.compact => double.infinity,
    WindowSize.medium => 760,
    WindowSize.expanded => 1080,
    WindowSize.large => 1320,
  };

  /// Forma va dialoglar uchun maksimal kenglik.
  double get formMaxWidth => isCompact ? double.infinity : 520;

  /// Ekran chetidagi bo'sh joy.
  double get pageGutter => switch (windowSize) {
    WindowSize.compact => 16,
    WindowSize.medium => 20,
    WindowSize.expanded => 24,
    WindowSize.large => 24,
  };

  EdgeInsets get pagePadding => EdgeInsets.all(pageGutter);

  /// Statistik kartochkalar gridi uchun ustunlar soni.
  int get statGridColumns => switch (windowSize) {
    WindowSize.compact => 2,
    WindowSize.medium => 3,
    WindowSize.expanded => 4,
    WindowSize.large => 4,
  };

  /// Berilgan minimal element kengligiga qarab ustunlar sonini hisoblaydi.
  int gridColumnsFor({double minItemWidth = 260, int maxColumns = 4}) {
    final usable = math.min(windowWidth, wideContentMaxWidth) - pageGutter * 2;
    final columns = (usable / minItemWidth).floor();
    return columns.clamp(1, maxColumns);
  }

  /// Ro'yxat + tafsilot (master–detail) yonma-yon ko'rsatilsinmi.
  bool get useSplitView => windowWidth >= Breakpoints.expanded;

  /// Logotip suv belgisi (watermark) o'lchami — ekranga moslashadi.
  double get watermarkSize {
    final shortest = math.min(windowWidth, windowHeight);
    return shortest.isFinite ? shortest.clamp(120.0, 420.0) * 0.7 : 220.0;
  }

  /// Chat pufakchasining maksimal kengligi.
  double get bubbleMaxWidth => math.min(windowWidth * 0.75, 560);
}
