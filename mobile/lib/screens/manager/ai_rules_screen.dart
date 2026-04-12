import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';
import '../../models/ai_rule_model.dart';
import '../../providers/ai_provider.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';

class AiRulesScreen extends ConsumerStatefulWidget {
  const AiRulesScreen({super.key});

  @override
  ConsumerState<AiRulesScreen> createState() => _AiRulesScreenState();
}

class _AiRulesScreenState extends ConsumerState<AiRulesScreen> {
  @override
  Widget build(BuildContext context) {
    final rulesAsync = ref.watch(aiRulesProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('AI Qoidalari'),
        actions: [
          IconButton(
            icon: const Icon(Icons.upload_file),
            tooltip: 'Fayldan yuklash',
            onPressed: () => _showUploadDialog(context),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showRuleDialog(context),
        child: const Icon(Icons.add),
      ),
      body: rulesAsync.when(
        loading: () => const LoadingWidget(),
        error: (error, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Qoidalar yuklanmadi',
                  style: TextStyle(color: theme.colorScheme.error)),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => ref.invalidate(aiRulesProvider),
                child: const Text('Qayta urinish'),
              ),
            ],
          ),
        ),
        data: (rules) {
          if (rules.isEmpty) {
            return const EmptyStateWidget(
              icon: Icons.rule,
              message: "AI qoidalari yo'q. + tugmani bosing.",
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
                        title: const Text("Qoidani o'chirish"),
                        content: Text(
                            '"${rule.title}" qoidasini o\'chirasizmi?'),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(ctx, false),
                            child: const Text('Bekor'),
                          ),
                          FilledButton(
                            onPressed: () => Navigator.pop(ctx, true),
                            style: FilledButton.styleFrom(
                              backgroundColor: theme.colorScheme.error,
                            ),
                            child: const Text("O'chirish"),
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
                        backgroundColor: rule.isActive
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
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            rule.content,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 4),
                          Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: theme.colorScheme.secondaryContainer,
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: Text(
                                  rule.category,
                                  style: TextStyle(
                                    fontSize: 10,
                                    color:
                                        theme.colorScheme.onSecondaryContainer,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 6),
                              Container(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: theme.colorScheme.surfaceContainerHighest,
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: Text(
                                  'Ustuvorlik: ${rule.priority}',
                                  style: const TextStyle(fontSize: 10),
                                ),
                              ),
                              const SizedBox(width: 6),
                              Container(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: rule.isActive
                                      ? Colors.green.shade100
                                      : Colors.grey.shade200,
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: Text(
                                  rule.isActive ? 'Faol' : 'Nofaol',
                                  style: TextStyle(
                                    fontSize: 10,
                                    color: rule.isActive
                                        ? Colors.green.shade800
                                        : Colors.grey.shade700,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                      isThreeLine: true,
                      onTap: () => _showRuleDialog(context, rule: rule),
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

  void _showRuleDialog(BuildContext context, {AiRule? rule}) {
    final isEditing = rule != null;
    final titleController = TextEditingController(text: rule?.title ?? '');
    final contentController = TextEditingController(text: rule?.content ?? '');
    final categoryController =
        TextEditingController(text: rule?.category ?? '');
    final priorityController =
        TextEditingController(text: rule?.priority.toString() ?? '5');
    bool isActive = rule?.isActive ?? true;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: Text(isEditing ? 'Qoidani tahrirlash' : 'Yangi qoida'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: titleController,
                  decoration: const InputDecoration(labelText: 'Sarlavha'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: contentController,
                  decoration: const InputDecoration(labelText: 'Kontent'),
                  maxLines: 4,
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: categoryController,
                  decoration:
                      const InputDecoration(labelText: 'Kategoriya'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: priorityController,
                  decoration:
                      const InputDecoration(labelText: 'Ustuvorlik (1-10)'),
                  keyboardType: TextInputType.number,
                ),
                const SizedBox(height: 12),
                SwitchListTile(
                  title: const Text('Faol'),
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
              child: const Text('Bekor'),
            ),
            FilledButton(
              onPressed: () async {
                final messenger = ScaffoldMessenger.of(context);
                final data = {
                  'title': titleController.text.trim(),
                  'content': contentController.text.trim(),
                  'category': categoryController.text.trim(),
                  'priority': int.tryParse(priorityController.text) ?? 5,
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
                messenger.showSnackBar(
                  SnackBar(
                    content: Text(success
                        ? (isEditing ? 'Qoida yangilandi' : 'Qoida yaratildi')
                        : 'Xatolik yuz berdi'),
                  ),
                );
              },
              child: Text(isEditing ? 'Yangilash' : "Qo'shish"),
            ),
          ],
        ),
      ),
    );
  }

  void _showUploadDialog(BuildContext context) {
    String? selectedFilePath;
    String? selectedFileName;
    final titleController = TextEditingController();
    final categoryController = TextEditingController();
    bool isUploading = false;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: const Text('Fayldan qoida yaratish'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  'Hujjat yuklanadi, matni o\'qiladi va qoida sifatida saqlanadi.',
                  style: TextStyle(fontSize: 13, color: Colors.grey),
                ),
                const SizedBox(height: 16),
                GestureDetector(
                  onTap: isUploading
                      ? null
                      : () async {
                          final result = await FilePicker.platform.pickFiles(
                            type: FileType.custom,
                            allowedExtensions: [
                              'pdf',
                              'docx',
                              'doc',
                              'txt',
                              'md'
                            ],
                          );
                          if (result != null &&
                              result.files.isNotEmpty) {
                            setDialogState(() {
                              selectedFilePath =
                                  result.files.first.path;
                              selectedFileName =
                                  result.files.first.name;
                            });
                          }
                        },
                  child: Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      border: Border.all(
                        color: selectedFilePath != null
                            ? Colors.blue
                            : Colors.grey.shade400,
                        width: 2,
                      ),
                      borderRadius: BorderRadius.circular(12),
                      color: selectedFilePath != null
                          ? Colors.blue.shade50
                          : Colors.grey.shade50,
                    ),
                    child: Column(
                      children: [
                        Icon(
                          selectedFilePath != null
                              ? Icons.description
                              : Icons.upload_file,
                          size: 40,
                          color: selectedFilePath != null
                              ? Colors.blue
                              : Colors.grey,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          selectedFileName ??
                              'Fayl tanlash uchun bosing',
                          style: TextStyle(
                            color: selectedFilePath != null
                                ? Colors.blue.shade700
                                : Colors.grey.shade600,
                            fontWeight: selectedFilePath != null
                                ? FontWeight.w600
                                : FontWeight.normal,
                          ),
                          textAlign: TextAlign.center,
                        ),
                        if (selectedFilePath == null)
                          Text(
                            'PDF, DOCX, TXT, DOC',
                            style: TextStyle(
                                fontSize: 11, color: Colors.grey.shade500),
                          ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: titleController,
                  decoration: const InputDecoration(
                    labelText: 'Sarlavha (ixtiyoriy)',
                    hintText: 'Fayl nomidan foydalaniladi',
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: categoryController,
                  decoration: const InputDecoration(
                    labelText: 'Kategoriya (ixtiyoriy)',
                    hintText: 'GENERAL',
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: isUploading ? null : () => Navigator.pop(ctx),
              child: const Text('Bekor'),
            ),
            FilledButton(
              onPressed: (selectedFilePath == null || isUploading)
                  ? null
                  : () async {
                      final messenger = ScaffoldMessenger.of(context);
                      setDialogState(() => isUploading = true);
                      try {
                        final apiService = ref.read(apiServiceProvider);
                        await apiService.uploadAiRuleFromFile(
                          selectedFilePath!,
                          selectedFileName!,
                          title: titleController.text.trim().isEmpty
                              ? null
                              : titleController.text.trim(),
                          category: categoryController.text.trim().isEmpty
                              ? null
                              : categoryController.text.trim(),
                        );
                        ref.invalidate(aiRulesProvider);
                        if (ctx.mounted) Navigator.pop(ctx);
                        messenger.showSnackBar(
                          const SnackBar(
                            content: Text("Fayl o'qildi va qoida yaratildi"),
                          ),
                        );
                      } catch (e) {
                        // ignore: avoid_dynamic_calls
                        final responseBody = (e as dynamic).response?.data?.toString() ?? '';
                        debugPrint('AI rule upload error: $e | body: $responseBody');
                        setDialogState(() => isUploading = false);
                        final msg = e.toString().contains('500')
                            ? 'Server xatosi. Fayl formati qo\'llab-quvvatlanmaydi.'
                            : e.toString().contains('401') || e.toString().contains('403')
                                ? 'Ruxsat yo\'q. Qayta kiring.'
                                : e.toString().contains('SocketException') ||
                                        e.toString().contains('Connection')
                                    ? 'Tarmoq xatosi. Internet aloqasini tekshiring.'
                                    : 'Xatolik: ${e.toString().length > 80 ? e.toString().substring(0, 80) : e.toString()}';
                        messenger.showSnackBar(
                          SnackBar(
                            content: Text(msg),
                            duration: const Duration(seconds: 5),
                          ),
                        );
                      }
                    },
              child: isUploading
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('Yuklash'),
            ),
          ],
        ),
      ),
    );
  }
}
