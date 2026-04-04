import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fl_chart/fl_chart.dart';
import '../../providers/kpi_provider.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';

class KpiDashboardScreen extends ConsumerWidget {
  const KpiDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rankingsAsync = ref.watch(kpiRankingsProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('KPI Dashboard'),
      ),
      body: rankingsAsync.when(
        loading: () => const LoadingWidget(),
        error: (error, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Failed to load KPI data',
                  style: TextStyle(color: theme.colorScheme.error)),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => ref.invalidate(kpiRankingsProvider),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
        data: (rankings) {
          if (rankings.isEmpty) {
            return const EmptyStateWidget(
              icon: Icons.bar_chart,
              message: 'No KPI data available yet.',
            );
          }

          // Sort by rank (nulls last, then by score descending)
          final sorted = List.of(rankings)
            ..sort((a, b) {
              final ra = a.rank ?? 999;
              final rb = b.rank ?? 999;
              return ra.compareTo(rb);
            });

          return RefreshIndicator(
            onRefresh: () async {
              ref.invalidate(kpiRankingsProvider);
              await ref.read(kpiRankingsProvider.future);
            },
            child: SingleChildScrollView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Bar chart
                  Text(
                    'Team Performance',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 16),
                  SizedBox(
                    height: 250,
                    child: BarChart(
                      BarChartData(
                        alignment: BarChartAlignment.spaceAround,
                        maxY: 100,
                        barTouchData: BarTouchData(
                          enabled: true,
                          touchTooltipData: BarTouchTooltipData(
                            getTooltipItem:
                                (group, groupIndex, rod, rodIndex) {
                              final name = sorted[group.x.toInt()].staffName;
                              return BarTooltipItem(
                                '$name\n${rod.toY.toStringAsFixed(1)}',
                                TextStyle(
                                  color: theme.colorScheme.onInverseSurface,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 12,
                                ),
                              );
                            },
                          ),
                        ),
                        titlesData: FlTitlesData(
                          show: true,
                          bottomTitles: AxisTitles(
                            sideTitles: SideTitles(
                              showTitles: true,
                              getTitlesWidget: (value, meta) {
                                final index = value.toInt();
                                if (index >= sorted.length) {
                                  return const SizedBox.shrink();
                                }
                                final name = sorted[index].staffName;
                                return Padding(
                                  padding: const EdgeInsets.only(top: 8),
                                  child: Text(
                                    name.length > 6
                                        ? '${name.substring(0, 6)}...'
                                        : name,
                                    style: const TextStyle(fontSize: 10),
                                  ),
                                );
                              },
                              reservedSize: 32,
                            ),
                          ),
                          leftTitles: AxisTitles(
                            sideTitles: SideTitles(
                              showTitles: true,
                              reservedSize: 32,
                              getTitlesWidget: (value, meta) {
                                return Text(
                                  value.toInt().toString(),
                                  style: const TextStyle(fontSize: 10),
                                );
                              },
                            ),
                          ),
                          topTitles: const AxisTitles(
                              sideTitles: SideTitles(showTitles: false)),
                          rightTitles: const AxisTitles(
                              sideTitles: SideTitles(showTitles: false)),
                        ),
                        gridData: FlGridData(
                          show: true,
                          drawVerticalLine: false,
                          horizontalInterval: 20,
                          getDrawingHorizontalLine: (value) => FlLine(
                            color: theme.colorScheme.outlineVariant,
                            strokeWidth: 1,
                          ),
                        ),
                        borderData: FlBorderData(show: false),
                        barGroups: sorted.asMap().entries.map((entry) {
                          final index = entry.key;
                          final kpi = entry.value;
                          return BarChartGroupData(
                            x: index,
                            barRods: [
                              BarChartRodData(
                                toY: kpi.score,
                                color: _getBarColor(index, theme),
                                width: 20,
                                borderRadius: const BorderRadius.vertical(
                                  top: Radius.circular(4),
                                ),
                              ),
                            ],
                          );
                        }).toList(),
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),

                  // Rankings table
                  Text(
                    'Rankings',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Card(
                    child: Column(
                      children: [
                        // Table header
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 16, vertical: 12),
                          decoration: BoxDecoration(
                            color: theme.colorScheme.surfaceContainerHighest,
                            borderRadius: const BorderRadius.vertical(
                              top: Radius.circular(12),
                            ),
                          ),
                          child: Row(
                            children: [
                              const SizedBox(
                                  width: 40,
                                  child: Text('Rank',
                                      style: TextStyle(
                                          fontWeight: FontWeight.bold,
                                          fontSize: 12))),
                              const Expanded(
                                  child: Text('Name',
                                      style: TextStyle(
                                          fontWeight: FontWeight.bold,
                                          fontSize: 12))),
                              SizedBox(
                                  width: 60,
                                  child: Text('Score',
                                      style: TextStyle(
                                          fontWeight: FontWeight.bold,
                                          fontSize: 12,
                                          color: theme
                                              .colorScheme.onSurfaceVariant),
                                      textAlign: TextAlign.right)),
                            ],
                          ),
                        ),
                        // Table rows
                        ...sorted.map((kpi) => Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 16, vertical: 12),
                              decoration: BoxDecoration(
                                border: Border(
                                  bottom: BorderSide(
                                    color: theme.colorScheme.outlineVariant,
                                    width: 0.5,
                                  ),
                                ),
                              ),
                              child: Row(
                                children: [
                                  SizedBox(
                                    width: 40,
                                    child: _buildRankBadge(
                                        context, kpi.rank ?? 0),
                                  ),
                                  Expanded(
                                    child: Text(
                                      kpi.staffName,
                                      style: const TextStyle(
                                          fontWeight: FontWeight.w500),
                                    ),
                                  ),
                                  SizedBox(
                                    width: 60,
                                    child: Text(
                                      kpi.score.toStringAsFixed(1),
                                      style: TextStyle(
                                        fontWeight: FontWeight.bold,
                                        color: theme.colorScheme.primary,
                                      ),
                                      textAlign: TextAlign.right,
                                    ),
                                  ),
                                ],
                              ),
                            )),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Color _getBarColor(int index, ThemeData theme) {
    switch (index) {
      case 0:
        return Colors.amber;
      case 1:
        return Colors.grey.shade400;
      case 2:
        return Colors.brown.shade300;
      default:
        return theme.colorScheme.primary;
    }
  }

  Widget _buildRankBadge(BuildContext context, int rank) {
    if (rank <= 3) {
      final colors = [Colors.amber, Colors.grey.shade400, Colors.brown.shade300];
      return CircleAvatar(
        radius: 14,
        backgroundColor: colors[rank - 1],
        child: Text(
          '#$rank',
          style: const TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.bold,
            color: Colors.white,
          ),
        ),
      );
    }
    return Text(
      '#$rank',
      style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13),
    );
  }
}
