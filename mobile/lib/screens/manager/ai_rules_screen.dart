import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';
import '../../models/ai_rule_model.dart';
import '../../providers/ai_provider.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';
import '../../widgets/app_background.dart';

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
      body: AppBackground(child: rulesAsync.when(
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
                  onDismissed: (_) async {
                    final messenger = ScaffoldMessenger.of(context);
                    final success = await ref
                        .read(aiRulesNotifierProvider.notifier)
                        .deleteRule(rule.id);
                    if (!success) {
                      messenger.showSnackBar(
                        const SnackBar(
                            content: Text("O'chirishda xatolik yuz berdi")),
                      );
                    }
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
                      trailing: IconButton(
                        icon: Icon(Icons.delete_outline,
                            color: theme.colorScheme.error),
                        tooltip: "O'chirish",
                        onPressed: () =>
                            _confirmDelete(context, rule.id, rule.title),
                      ),
                      onTap: () => _showRuleDialog(context, rule: rule),
                    ),
                  ),
                );
              },
            ),
          );
        },
      )),
    );
  }

  Future<void> _confirmDelete(
      BuildContext context, String id, String title) async {
    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text("Qoidani o'chirish"),
        content: Text('"$title" qoidasini o\'chirasizmi?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Bekor'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            child: const Text("O'chirish"),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      final success =
          await ref.read(aiRulesNotifierProvider.notifier).deleteRule(id);
      messenger.showSnackBar(
        SnackBar(
          content: Text(
              success ? "Qoida o'chirildi" : "O'chirishda xatolik yuz berdi"),
        ),
      );
    }
  }

  void _showRuleDialog(BuildContext context, {AiRule? rule}) {
    final isEditing = rule != null;
    final titleController = TextEditingController(text: rule?.title ?? '');
    final contentController = TextEditingController(text: rule?.content ?? '');
    final categoryController =
        TextEditingController(text: rule?.category ?? '');
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
    final List<PlatformFile> selectedFiles = [];
    final categoryController = TextEditingController();
    bool isUploading = false;
    int currentIndex = 0;
    int totalFiles = 0;

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: const Text('Fayldan qoidalar yaratish'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  "Bir nechta fayl tanlang — har bir fayl alohida qoida sifatida saqlanadi.",
                  style: TextStyle(fontSize: 13, color: Colors.grey),
                ),
                const SizedBox(height: 16),
                GestureDetector(
                  onTap: isUploading
                      ? null
                      : () async {
                          final result = await FilePicker.platform.pickFiles(
                            type: FileType.custom,
                            allowMultiple: true,
                            allowedExtensions: [
                              'pdf',
                              'docx',
                              'doc',
                              'txt',
                              'md'
                            ],
                          );
                          if (result != null && result.files.isNotEmpty) {
                            setDialogState(() {
                              for (final f in result.files) {
                                if (f.path != null &&
                                    !selectedFiles
                                        .any((s) => s.path == f.path)) {
                                  selectedFiles.add(f);
                                }
                              }
                            });
                          }
                        },
                  child: Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      border: Border.all(
                        color: selectedFiles.isNotEmpty
                            ? Colors.blue
                            : Colors.grey.shade400,
                        width: 2,
                      ),
                      borderRadius: BorderRadius.circular(12),
                      color: selectedFiles.isNotEmpty
                          ? Colors.blue.shade50
                          : Colors.grey.shade50,
                    ),
                    child: Column(
                      children: [
                        Icon(
                          selectedFiles.isNotEmpty
                              ? Icons.library_add
                              : Icons.upload_file,
                          size: 40,
                          color: selectedFiles.isNotEmpty
                              ? Colors.blue
                              : Colors.grey,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          selectedFiles.isEmpty
                              ? "Fayllarni tanlash uchun bosing"
                              : "Yana fayl qo'shish uchun bosing",
                          style: TextStyle(
                            color: selectedFiles.isNotEmpty
                                ? Colors.blue.shade700
                                : Colors.grey.shade600,
                            fontWeight: selectedFiles.isNotEmpty
                                ? FontWeight.w600
                                : FontWeight.normal,
                          ),
                          textAlign: TextAlign.center,
                        ),
                        if (selectedFiles.isEmpty)
                          Text(
                            'PDF, DOCX, TXT, DOC, MD',
                            style: TextStyle(
                                fontSize: 11, color: Colors.grey.shade500),
                          ),
                      ],
                    ),
                  ),
                ),
                if (selectedFiles.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Text(
                    'Tanlangan: ${selectedFiles.length} ta fayl',
                    style: const TextStyle(
                        fontSize: 12, fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 8),
                  Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      for (int i = 0; i < selectedFiles.length; i++)
                        Builder(builder: (_) {
                          final f = selectedFiles[i];
                          final isCurrent = isUploading && i == currentIndex;
                          final isDone = isUploading && i < currentIndex;
                          return ListTile(
                            dense: true,
                            contentPadding:
                                const EdgeInsets.symmetric(horizontal: 4),
                            leading: isDone
                                ? const Icon(Icons.check_circle,
                                    color: Colors.green, size: 20)
                                : isCurrent
                                    ? const SizedBox(
                                        width: 20,
                                        height: 20,
                                        child: CircularProgressIndicator(
                                            strokeWidth: 2),
                                      )
                                    : const Icon(Icons.description,
                                        size: 20, color: Colors.blueGrey),
                            title: Text(
                              f.name,
                              style: const TextStyle(fontSize: 13),
                              overflow: TextOverflow.ellipsis,
                            ),
                            subtitle: Text(
                              '${(f.size / 1024).toStringAsFixed(1)} KB',
                              style: const TextStyle(fontSize: 11),
                            ),
                            trailing: isUploading
                                ? null
                                : IconButton(
                                    icon: const Icon(Icons.close, size: 18),
                                    onPressed: () => setDialogState(
                                        () => selectedFiles.removeAt(i)),
                                  ),
                          );
                        }),
                    ],
                  ),
                ],
                const SizedBox(height: 12),
                TextField(
                  controller: categoryController,
                  enabled: !isUploading,
                  decoration: const InputDecoration(
                    labelText: 'Kategoriya (ixtiyoriy)',
                    hintText: 'Barcha fayllarga qo\'llaniladi — masalan GENERAL',
                  ),
                ),
                if (isUploading && totalFiles > 0) ...[
                  const SizedBox(height: 16),
                  LinearProgressIndicator(
                    value: totalFiles == 0
                        ? null
                        : currentIndex / totalFiles,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Yuklanmoqda: $currentIndex / $totalFiles',
                    style: const TextStyle(fontSize: 12),
                    textAlign: TextAlign.center,
                  ),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: isUploading ? null : () => Navigator.pop(ctx),
              child: const Text('Bekor'),
            ),
            FilledButton(
              onPressed: (selectedFiles.isEmpty || isUploading)
                  ? null
                  : () async {
                      final messenger = ScaffoldMessenger.of(context);

                      // Hajm tekshiruvi — har bir fayl uchun 50MB limit
                      const maxBytes = 50 * 1024 * 1024;
                      for (final f in selectedFiles) {
                        if (f.size > maxBytes) {
                          messenger.showSnackBar(
                            SnackBar(
                              content: Text(
                                "${f.name}: hajmi ${(f.size / 1024 / 1024).toStringAsFixed(1)} MB — "
                                "maksimum 50 MB.",
                              ),
                              duration: const Duration(seconds: 5),
                            ),
                          );
                          return;
                        }
                      }

                      setDialogState(() {
                        isUploading = true;
                        currentIndex = 0;
                        totalFiles = selectedFiles.length;
                      });

                      final apiService = ref.read(apiServiceProvider);
                      final category = categoryController.text.trim();
                      int successCount = 0;
                      final List<String> failedFiles = [];

                      for (int i = 0; i < selectedFiles.length; i++) {
                        setDialogState(() => currentIndex = i);
                        final file = selectedFiles[i];
                        try {
                          await apiService.uploadAiRuleFromFile(
                            file.path!,
                            file.name,
                            category: category.isEmpty ? null : category,
                          );
                          successCount++;
                        } catch (e) {
                          // ignore: avoid_dynamic_calls
                          final responseBody =
                              (e as dynamic).response?.data?.toString() ?? '';
                          debugPrint(
                              'AI rule upload error (${file.name}): $e | body: $responseBody');
                          failedFiles.add(file.name);
                        }
                      }

                      setDialogState(() => currentIndex = totalFiles);

                      ref.invalidate(aiRulesProvider);
                      if (ctx.mounted) Navigator.pop(ctx);

                      final String msg;
                      if (failedFiles.isEmpty) {
                        msg = "$successCount ta qoida yaratildi";
                      } else if (successCount == 0) {
                        msg = "Barcha fayllarda xatolik yuz berdi";
                      } else {
                        msg =
                            "$successCount ta qoida yaratildi, ${failedFiles.length} ta xato: ${failedFiles.join(', ')}";
                      }
                      messenger.showSnackBar(
                        SnackBar(
                          content: Text(msg),
                          duration: const Duration(seconds: 5),
                        ),
                      );
                    },
              child: isUploading
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(selectedFiles.isEmpty
                      ? "Yuklash"
                      : "${selectedFiles.length} ta yuklash"),
            ),
          ],
        ),
      ),
    );
  }
}
