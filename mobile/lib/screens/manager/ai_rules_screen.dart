import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../models/ai_rule_model.dart';
import '../../providers/ai_provider.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';

class AiRulesScreen extends ConsumerWidget {
  const AiRulesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rulesAsync = ref.watch(aiRulesProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('AI Rules'),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showRuleDialog(context, ref),
        child: const Icon(Icons.add),
      ),
      body: rulesAsync.when(
        loading: () => const LoadingWidget(),
        error: (error, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Failed to load rules',
                  style: TextStyle(color: theme.colorScheme.error)),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => ref.invalidate(aiRulesProvider),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
        data: (rules) {
          if (rules.isEmpty) {
            return const EmptyStateWidget(
              icon: Icons.rule,
              message: 'No AI rules yet. Tap + to create one.',
            );
          }

          return RefreshIndicator(
            onRefresh: () async {
              ref.invalidate(aiRulesProvider);
              await ref.read(aiRulesProvider.future);
            },
            child: ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: rules.length,
              itemBuilder: (context, index) {
                final rule = rules[index];
                return Dismissible(
                  key: Key(rule.id),
                  direction: DismissDirection.endToStart,
                  background: Container(
                    alignment: Alignment.centerRight,
                    padding: const EdgeInsets.only(right: 20),
                    margin: const EdgeInsets.only(bottom: 8),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.error,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(Icons.delete, color: theme.colorScheme.onError),
                  ),
                  confirmDismiss: (direction) async {
                    return await showDialog<bool>(
                      context: context,
                      builder: (ctx) => AlertDialog(
                        title: const Text('Delete Rule'),
                        content: Text(
                            'Delete "${rule.title}"? This cannot be undone.'),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(ctx, false),
                            child: const Text('Cancel'),
                          ),
                          FilledButton(
                            onPressed: () => Navigator.pop(ctx, true),
                            style: FilledButton.styleFrom(
                              backgroundColor: theme.colorScheme.error,
                            ),
                            child: const Text('Delete'),
                          ),
                        ],
                      ),
                    );
                  },
                  onDismissed: (_) {
                    ref
                        .read(aiRulesNotifierProvider.notifier)
                        .deleteRule(rule.id);
                  },
                  child: Card(
                    margin: const EdgeInsets.only(bottom: 8),
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor:
                            rule.isActive
                                ? theme.colorScheme.primaryContainer
                                : theme.colorScheme.surfaceContainerHighest,
                        child: Icon(
                          Icons.rule,
                          color: rule.isActive
                              ? theme.colorScheme.onPrimaryContainer
                              : theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      title: Text(
                        rule.title,
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                      subtitle: Text(
                        rule.content,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      trailing: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Chip(
                            label: Text(
                              rule.category,
                              style: const TextStyle(fontSize: 10),
                            ),
                            padding: EdgeInsets.zero,
                            visualDensity: VisualDensity.compact,
                          ),
                        ],
                      ),
                      onTap: () => _showRuleDialog(context, ref, rule: rule),
                    ),
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }

  void _showRuleDialog(BuildContext context, WidgetRef ref,
      {AiRule? rule}) {
    final isEditing = rule != null;
    final titleController = TextEditingController(text: rule?.title ?? '');
    final contentController = TextEditingController(text: rule?.content ?? '');
    final categoryController =
        TextEditingController(text: rule?.category ?? '');
    final priorityController =
        TextEditingController(text: rule?.priority.toString() ?? '0');
    bool isActive = rule?.isActive ?? true;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: Text(isEditing ? 'Edit Rule' : 'Create Rule'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: titleController,
                  decoration: const InputDecoration(labelText: 'Title'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: contentController,
                  decoration: const InputDecoration(labelText: 'Content'),
                  maxLines: 3,
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: categoryController,
                  decoration: const InputDecoration(labelText: 'Category'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: priorityController,
                  decoration: const InputDecoration(labelText: 'Priority'),
                  keyboardType: TextInputType.number,
                ),
                const SizedBox(height: 12),
                SwitchListTile(
                  title: const Text('Active'),
                  value: isActive,
                  onChanged: (v) => setDialogState(() => isActive = v),
                  contentPadding: EdgeInsets.zero,
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () async {
                final data = {
                  'title': titleController.text.trim(),
                  'content': contentController.text.trim(),
                  'category': categoryController.text.trim(),
                  'priority': int.tryParse(priorityController.text) ?? 0,
                  'isActive': isActive,
                };

                bool success;
                if (isEditing) {
                  success = await ref
                      .read(aiRulesNotifierProvider.notifier)
                      .updateRule(rule.id, data);
                } else {
                  success = await ref
                      .read(aiRulesNotifierProvider.notifier)
                      .createRule(data);
                }

                if (ctx.mounted) Navigator.pop(ctx);
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(success
                          ? (isEditing ? 'Rule updated' : 'Rule created')
                          : 'Operation failed'),
                    ),
                  );
                }
              },
              child: Text(isEditing ? 'Update' : 'Create'),
            ),
          ],
        ),
      ),
    );
  }
}
