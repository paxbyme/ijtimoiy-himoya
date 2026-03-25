import 'dart:math';
import 'package:flutter/material.dart';

class KpiGauge extends StatelessWidget {
  final double score;

  const KpiGauge({super.key, required this.score});

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _KpiGaugePainter(
        score: score,
        primaryColor: Theme.of(context).colorScheme.primary,
        backgroundColor: Theme.of(context).colorScheme.surfaceContainerHighest,
        textColor: Theme.of(context).colorScheme.onSurface,
      ),
    );
  }
}

class _KpiGaugePainter extends CustomPainter {
  final double score;
  final Color primaryColor;
  final Color backgroundColor;
  final Color textColor;

  _KpiGaugePainter({
    required this.score,
    required this.primaryColor,
    required this.backgroundColor,
    required this.textColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = min(size.width, size.height) / 2 - 8;
    const strokeWidth = 12.0;
    const startAngle = -pi * 0.75;
    const sweepAngle = pi * 1.5;
    final progress = (score / 100).clamp(0.0, 1.0);

    // Background arc
    final bgPaint = Paint()
      ..color = backgroundColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round;

    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      startAngle,
      sweepAngle,
      false,
      bgPaint,
    );

    // Progress arc
    Color progressColor;
    if (score >= 80) {
      progressColor = Colors.green;
    } else if (score >= 60) {
      progressColor = Colors.orange;
    } else if (score >= 40) {
      progressColor = Colors.amber;
    } else {
      progressColor = Colors.red;
    }

    final progressPaint = Paint()
      ..color = progressColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round;

    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      startAngle,
      sweepAngle * progress,
      false,
      progressPaint,
    );

    // Score text
    final textPainter = TextPainter(
      text: TextSpan(
        text: score.toStringAsFixed(1),
        style: TextStyle(
          color: textColor,
          fontSize: radius * 0.45,
          fontWeight: FontWeight.bold,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();

    textPainter.paint(
      canvas,
      Offset(
        center.dx - textPainter.width / 2,
        center.dy - textPainter.height / 2,
      ),
    );

    // Label text
    final labelPainter = TextPainter(
      text: TextSpan(
        text: '/100',
        style: TextStyle(
          color: textColor.withValues(alpha: 0.5),
          fontSize: radius * 0.2,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();

    labelPainter.paint(
      canvas,
      Offset(
        center.dx - labelPainter.width / 2,
        center.dy + textPainter.height / 2 + 2,
      ),
    );
  }

  @override
  bool shouldRepaint(covariant _KpiGaugePainter oldDelegate) {
    return oldDelegate.score != score;
  }
}
