import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/kpi_provider.dart';
import '../../widgets/kpi_gauge.dart';
import '../../widgets/loading_widget.dart';

class KpiScreen extends ConsumerWidget {
  const KpiScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final kpiAsync = ref.watch(myKpiProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('My KPIs'),
      ),
      body: kpiAsync.when(
        loading: () => const LoadingWidget(),
        error: (error, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.bar_chart, size: 64,
                  color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.5)),
              const SizedBox(height: 16),
              Text('Unable to load KPI data',
                  style: theme.textTheme.bodyLarge),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => ref.invalidate(myKpiProvider),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
        data: (kpi) => RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(myKpiProvider);
            await ref.read(myKpiProvider.future);
          },
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                // Score gauge
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      children: [
                        Text(
                          'Overall Score',
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 24),
                        SizedBox(
                          width: 180,
                          height: 180,
                          child: KpiGauge(score: kpi.score),
                        ),
                        const SizedBox(height: 16),
                        Text(
                          kpi.period,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Ranking
                Card(
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor: theme.colorScheme.primaryContainer,
                      child: Text(
                        '#${kpi.rank}',
                        style: TextStyle(
                          color: theme.colorScheme.onPrimaryContainer,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    title: const Text('Department Ranking'),
                    subtitle: Text('You are ranked #${kpi.rank} in your department'),
                  ),
                ),
                const SizedBox(height: 16),

                // Breakdown
                if (kpi.breakdown != null) ...[
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Performance Breakdown',
                            style: theme.textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 16),
                          _buildBreakdownBar(
                            context,
                            'Timeliness',
                            kpi.breakdown!.timeliness,
                            Colors.blue,
                          ),
                          const SizedBox(height: 12),
                          _buildBreakdownBar(
                            context,
                            'Completion',
                            kpi.breakdown!.completion,
                            Colors.green,
                          ),
                          const SizedBox(height: 12),
                          _buildBreakdownBar(
                            context,
                            'Efficiency',
                            kpi.breakdown!.efficiency,
                            Colors.orange,
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildBreakdownBar(
      BuildContext context, String label, double value, Color color) {
    final theme = Theme.of(context);
    final percentage = (value * 100).clamp(0, 100);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(label, style: theme.textTheme.bodyMedium),
            Text(
              '${percentage.toStringAsFixed(0)}%',
              style: theme.textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        ClipRRect(
          borderRadius: BorderRadius.circular(4),
          child: LinearProgressIndicator(
            value: value.clamp(0, 1),
            backgroundColor: color.withValues(alpha: 0.15),
            valueColor: AlwaysStoppedAnimation(color),
            minHeight: 8,
          ),
        ),
      ],
    );
  }
}
