import 'dart:io';

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

                      // Fayl hajmini tekshirish (50MB limit)
                      const maxBytes = 50 * 1024 * 1024;
                      final fileSize = File(selectedFilePath!).lengthSync();
                      if (fileSize > maxBytes) {
                        messenger.showSnackBar(
                          SnackBar(
                            content: Text(
                              'Fayl hajmi ${(fileSize / 1024 / 1024).toStringAsFixed(1)} MB — '
                              "ruxsat etilgan maksimum 50 MB.",
                            ),
                            duration: const Duration(seconds: 5),
                          ),
                        );
                        return;
                      }

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
                        final responseBody =
                            (e as dynamic).response?.data?.toString() ?? '';
                        debugPrint(
                            'AI rule upload error: $e | body: $responseBody');
                        setDialogState(() => isUploading = false);
                        final errStr = e.toString();
                        final String msg;
                        if (responseBody.contains('scanned image') ||
                            responseBody.contains('selectable text') ||
                            responseBody.contains('Could not extract readable')) {
                          msg =
                              "Hujjatdan matn o'qib bo'lmadi (skanerlangan rasm yoki OCR ham ishlamadi). "
                              "Matn sifatida saqlangan fayl yuboring yoki qoidani qo'lda kiriting.";
                        } else if (errStr.contains('413') ||
                            errStr.contains('exceeds') ||
                            errStr.contains('upload size')) {
                          msg = "Fayl hajmi juda katta (maksimum 50 MB).";
                        } else if (errStr.contains('Connection reset') ||
                            errStr.contains('reset by peer') ||
                            errStr.contains('Broken pipe') ||
                            errStr.contains('errno = 32')) {
                          msg =
                              "Server ulanishni uzdi. Fayl hajmi juda katta bo'lishi mumkin.";
                        } else if (errStr.contains('401') ||
                            errStr.contains('403')) {
                          msg = "Ruxsat yo'q. Qayta kiring.";
                        } else if (errStr.contains('500')) {
                          msg =
                              "Server xatosi. Fayl formati qo'llab-quvvatlanmaydi.";
                        } else if (errStr.contains('TimeoutException') ||
                            errStr.contains('timeout')) {
                          msg =
                              "Vaqt tugadi. Internet aloqasini tekshiring yoki kichikroq fayl yuklang.";
                        } else if (errStr.contains('SocketException') &&
                            !errStr.contains('reset')) {
                          msg = "Tarmoq xatosi. Internet aloqasini tekshiring.";
                        } else {
                          msg =
                              'Xatolik: ${errStr.length > 100 ? errStr.substring(0, 100) : errStr}';
                        }
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
