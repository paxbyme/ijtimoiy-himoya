import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:image_picker/image_picker.dart';
import 'package:file_picker/file_picker.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../models/task_model.dart';
import '../../providers/task_provider.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/app_background.dart';

class TaskDetailScreen extends ConsumerStatefulWidget {
  final String taskId;

  const TaskDetailScreen({super.key, required this.taskId});

  @override
  ConsumerState<TaskDetailScreen> createState() => _TaskDetailScreenState();
}

class _TaskDetailScreenState extends ConsumerState<TaskDetailScreen> {
  bool _isUploading = false;
  bool _isCompleting = false;
  bool _isUpdatingStatus = false;
  final _imagePicker = ImagePicker();

  void _showUploadBottomSheet(Task task) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 40,
              height: 4,
              margin: const EdgeInsets.symmetric(vertical: 12),
              decoration: BoxDecoration(
                color: Colors.grey[300],
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.camera_alt),
              title: const Text('Kamera orqali rasm olish'),
              onTap: () {
                Navigator.pop(ctx);
                _pickFromCamera(task);
              },
            ),
            ListTile(
              leading: const Icon(Icons.photo_library),
              title: const Text('Galereyadan rasm tanlash'),
              onTap: () {
                Navigator.pop(ctx);
                _pickFromGallery(task);
              },
            ),
            ListTile(
              leading: const Icon(Icons.attach_file),
              title: const Text('Fayl yuklash'),
              onTap: () {
                Navigator.pop(ctx);
                _pickFile(task);
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  Future<void> _pickFromCamera(Task task) async {
    final XFile? photo = await _imagePicker.pickImage(
      source: ImageSource.camera,
      imageQuality: 85,
    );
    if (photo == null) return;
    await _uploadSingleFile(task, photo.path, photo.name);
  }

  Future<void> _pickFromGallery(Task task) async {
    final List<XFile> images = await _imagePicker.pickMultiImage(imageQuality: 85);
    if (images.isEmpty) return;

    setState(() => _isUploading = true);
    int errors = 0;
    for (final image in images) {
      final error = await ref
          .read(taskNotifierProvider.notifier)
          .uploadAttachment(task.id, image.path, image.name);
      if (error != null) errors++;
    }
    if (mounted) {
      setState(() => _isUploading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(errors == 0
              ? '${images.length} ta rasm muvaffaqiyatli yuklandi'
              : '$errors ta yuklashda xatolik yuz berdi'),
          backgroundColor: errors == 0 ? null : Colors.red,
          duration: Duration(seconds: errors == 0 ? 3 : 6),
        ),
      );
    }
  }

  Future<void> _pickFile(Task task) async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.any,
      allowMultiple: false,
    );
    if (result == null || result.files.isEmpty) return;
    final file = result.files.first;
    if (file.path == null) return;
    await _uploadSingleFile(task, file.path!, file.name);
  }

  Future<void> _uploadSingleFile(Task task, String path, String name) async {
    setState(() => _isUploading = true);
    try {
      final error = await ref
          .read(taskNotifierProvider.notifier)
          .uploadAttachment(task.id, path, name);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(error == null
                ? 'Fayl muvaffaqiyatli yuklandi'
                : 'Xatolik: $error'),
            backgroundColor: error != null ? Colors.red : null,
            duration: Duration(seconds: error != null ? 6 : 3),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isUploading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final tasksAsync = ref.watch(myTasksProvider);
    final userProfile = ref.watch(userProfileProvider);
    final theme = Theme.of(context);

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) context.pop();
      },
      child: Scaffold(
        appBar: AppBar(
          leading: BackButton(onPressed: () => context.pop()),
          title: const Text('Topshiriq tafsiloti'),
        ),
        body: Stack(
          children: [
            AppBackground(
              child: tasksAsync.when(
                loading: () => const LoadingWidget(),
                error: (error, _) => Center(child: Text('Xatolik: $error')),
                data: (tasks) {
                  final task = tasks.cast<Task?>().firstWhere(
                        (t) => t!.id == widget.taskId,
                        orElse: () => null,
                      );

                  if (task == null) {
                    return const Center(child: Text('Topshiriq topilmadi'));
                  }

                  return SingleChildScrollView(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          task.title,
                          style: theme.textTheme.headlineSmall
                              ?.copyWith(fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 12),

                        Row(
                          children: [
                            _buildStatusChip(context, task),
                            const SizedBox(width: 8),
                            _buildPriorityChip(context, task.priority),
                          ],
                        ),
                        const SizedBox(height: 24),

                        Text(
                          'Tavsif',
                          style: theme.textTheme.titleMedium
                              ?.copyWith(fontWeight: FontWeight.w600),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          task.description.isNotEmpty
                              ? task.description
                              : "Tavsif yo'q",
                          style: theme.textTheme.bodyLarge?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                        const SizedBox(height: 24),

                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Column(
                              children: [
                                _buildDetailRow(
                                  context,
                                  'Tayinlagan',
                                  userProfile.maybeWhen(
                                    data: (u) =>
                                        u?.managerId == task.assignedBy
                                            ? 'Menejer'
                                            : task.assignedBy,
                                    orElse: () => task.assignedBy,
                                  ),
                                  Icons.person_outline,
                                ),
                                if (task.deadline != null) ...[
                                  const Divider(),
                                  _buildDetailRow(
                                    context,
                                    'Muddat',
                                    DateFormat('dd.MM.yyyy').format(task.deadline!),
                                    Icons.calendar_today,
                                  ),
                                ],
                                if (task.completedAt != null) ...[
                                  const Divider(),
                                  _buildDetailRow(
                                    context,
                                    'Bajarilgan sana',
                                    DateFormat('dd.MM.yyyy').format(task.completedAt!),
                                    Icons.check_circle_outline,
                                  ),
                                ],
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(height: 16),

                        // Attachments section
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Icon(Icons.attach_file,
                                        size: 20,
                                        color: theme.colorScheme.primary),
                                    const SizedBox(width: 8),
                                    Text(
                                      'Fayllar',
                                      style: theme.textTheme.titleSmall?.copyWith(
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                    if (task.attachments.isNotEmpty) ...[
                                      const SizedBox(width: 6),
                                      Container(
                                        padding: const EdgeInsets.symmetric(
                                            horizontal: 7, vertical: 1),
                                        decoration: BoxDecoration(
                                          color: theme.colorScheme.primaryContainer,
                                          borderRadius: BorderRadius.circular(10),
                                        ),
                                        child: Text(
                                          '${task.attachments.length}',
                                          style: theme.textTheme.labelSmall?.copyWith(
                                            color: theme.colorScheme.onPrimaryContainer,
                                            fontWeight: FontWeight.bold,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ],
                                ),
                                const SizedBox(height: 12),

                                if (task.attachments.isEmpty)
                                  Text(
                                    'Hali fayl yuklanmagan',
                                    style: theme.textTheme.bodyMedium?.copyWith(
                                      color: theme.colorScheme.onSurfaceVariant,
                                    ),
                                  )
                                else
                                  ...task.attachments.map((att) => Padding(
                                        padding: const EdgeInsets.only(bottom: 8),
                                        child: Row(
                                          children: [
                                            Icon(
                                              _isImageName(att.name)
                                                  ? Icons.image_outlined
                                                  : Icons.description_outlined,
                                              size: 18,
                                              color: theme.colorScheme.primary,
                                            ),
                                            const SizedBox(width: 8),
                                            Expanded(
                                              child: Text(
                                                att.name,
                                                style: theme.textTheme.bodyMedium
                                                    ?.copyWith(
                                                  color: theme.colorScheme.primary,
                                                ),
                                                overflow: TextOverflow.ellipsis,
                                              ),
                                            ),
                                            IconButton(
                                              icon: const Icon(Icons.open_in_new,
                                                  size: 18),
                                              color: theme.colorScheme.primary,
                                              tooltip: 'Ochish',
                                              padding: EdgeInsets.zero,
                                              constraints: const BoxConstraints(),
                                              onPressed: () => _openAttachment(
                                                  context, att.url, att.name),
                                            ),
                                          ],
                                        ),
                                      )),

                                if (task.managerAccepted) ...[
                                  const SizedBox(height: 4),
                                  Row(
                                    children: [
                                      const Icon(Icons.verified,
                                          size: 16, color: Colors.green),
                                      const SizedBox(width: 4),
                                      Text(
                                        'Menejer tomonidan qabul qilingan',
                                        style: theme.textTheme.bodySmall
                                            ?.copyWith(color: Colors.green),
                                      ),
                                    ],
                                  ),
                                ],

                                const SizedBox(height: 12),
                                if (task.status != 'COMPLETED')
                                  SizedBox(
                                    width: double.infinity,
                                    child: OutlinedButton.icon(
                                      onPressed: _isUploading
                                          ? null
                                          : () => _showUploadBottomSheet(task),
                                      icon: _isUploading
                                          ? const SizedBox(
                                              width: 16,
                                              height: 16,
                                              child: CircularProgressIndicator(
                                                  strokeWidth: 2),
                                            )
                                          : const Icon(Icons.add_photo_alternate,
                                              size: 18),
                                      label: Text(_isUploading
                                          ? 'Yuklanmoqda...'
                                          : task.attachments.isNotEmpty
                                              ? "Yana fayl qo'shish"
                                              : 'Fayl / rasm yuklash'),
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(height: 24),

                        if (task.status != 'COMPLETED') ...[
                          if (task.status != 'IN_PROGRESS')
                            SizedBox(
                              width: double.infinity,
                              child: OutlinedButton.icon(
                                onPressed: (_isUpdatingStatus || _isCompleting)
                                    ? null
                                    : () => _updateStatus(task, 'IN_PROGRESS'),
                                icon: _isUpdatingStatus
                                    ? const SizedBox(
                                        width: 18,
                                        height: 18,
                                        child: CircularProgressIndicator(
                                            strokeWidth: 2),
                                      )
                                    : const Icon(Icons.play_arrow),
                                label: Text(_isUpdatingStatus
                                    ? 'Saqlanmoqda...'
                                    : 'Ish boshlandı'),
                              ),
                            ),
                          const SizedBox(height: 8),
                          SizedBox(
                            width: double.infinity,
                            child: ElevatedButton.icon(
                              onPressed: _isCompleting
                                  ? null
                                  : () => _confirmComplete(context, ref, task),
                              icon: _isCompleting
                                  ? const SizedBox(
                                      width: 18,
                                      height: 18,
                                      child: CircularProgressIndicator(
                                          strokeWidth: 2),
                                    )
                                  : const Icon(Icons.check_circle),
                              label: Text(_isCompleting
                                  ? 'Saqlanmoqda...'
                                  : 'Bajarildi deb belgilash'),
                            ),
                          ),
                        ],
                      ],
                    ),
                  );
                },
              ),
            ),
            if (_isCompleting)
              Container(
                color: Colors.black.withValues(alpha: 0.35),
                child: const Center(child: CircularProgressIndicator()),
              ),
          ],
        ),
      ),
    );
  }

  bool _isImageName(String? name) {
    if (name == null) return false;
    final ext = name.split('.').last.toLowerCase();
    return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].contains(ext);
  }

  Future<void> _openAttachment(
      BuildContext context, String url, String? name) async {
    if (_isImageName(name)) {
      if (!context.mounted) return;
      await showDialog(
        context: context,
        builder: (ctx) => Dialog(
          backgroundColor: Colors.black,
          insetPadding: EdgeInsets.zero,
          child: Stack(
            children: [
              InteractiveViewer(
                child: Center(
                  child: Image.network(
                    url,
                    fit: BoxFit.contain,
                    loadingBuilder: (c, child, progress) => progress == null
                        ? child
                        : const Center(child: CircularProgressIndicator()),
                    errorBuilder: (c, e, s) => const Center(
                      child: Text('Rasm yuklanmadi',
                          style: TextStyle(color: Colors.white)),
                    ),
                  ),
                ),
              ),
              Positioned(
                top: 8,
                right: 8,
                child: IconButton(
                  icon: const Icon(Icons.close, color: Colors.white, size: 28),
                  onPressed: () => Navigator.pop(ctx),
                ),
              ),
            ],
          ),
        ),
      );
    } else {
      final uri = Uri.parse(url);
      bool opened = false;
      try {
        opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
      } catch (_) {}
      if (!opened) {
        try {
          await launchUrl(uri, mode: LaunchMode.platformDefault);
        } catch (_) {}
      }
    }
  }

  Future<void> _updateStatus(Task task, String status) async {
    setState(() => _isUpdatingStatus = true);
    final success =
        await ref.read(taskNotifierProvider.notifier).updateStatus(task.id, status);
    if (!mounted) return;
    setState(() => _isUpdatingStatus = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
            success ? 'Holat yangilandi!' : 'Holatni yangilashda xatolik'),
      ),
    );
  }

  void _confirmComplete(BuildContext context, WidgetRef ref, Task task) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Topshiriqni yakunlash'),
        content: Text('"${task.title}" ni bajarildi deb belgilash?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Bekor'),
          ),
          FilledButton(
            onPressed: () async {
              Navigator.pop(ctx);
              if (mounted) setState(() => _isCompleting = true);
              final success = await ref
                  .read(taskNotifierProvider.notifier)
                  .completeTask(task.id);
              if (context.mounted) {
                setState(() => _isCompleting = false);
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(success
                        ? 'Topshiriq bajarildi!'
                        : 'Yakunlashda xatolik'),
                  ),
                );
              }
            },
            child: const Text('Bajarildi'),
          ),
        ],
      ),
    );
  }

  Widget _buildStatusChip(BuildContext context, Task task) {
    Color color;
    String label;
    if (task.isOverdue) {
      color = Colors.red;
      label = "Muddati o'tgan";
    } else {
      switch (task.status) {
        case 'NEW':
          color = Colors.orange;
          label = 'Yangi';
          break;
        case 'IN_PROGRESS':
          color = Colors.blue;
          label = 'Jarayonda';
          break;
        case 'COMPLETED':
          color = Colors.green;
          label = 'Bajarildi';
          break;
        default:
          color = Colors.grey;
          label = task.status.replaceAll('_', ' ');
      }
    }

    return Chip(
      label: Text(label, style: TextStyle(color: color, fontSize: 12)),
      side: BorderSide(color: color),
      backgroundColor: color.withValues(alpha: 0.1),
      padding: EdgeInsets.zero,
      visualDensity: VisualDensity.compact,
    );
  }

  Widget _buildPriorityChip(BuildContext context, String priority) {
    Color color;
    String label;
    switch (priority) {
      case 'HIGH':
        color = Colors.red;
        label = 'Yuqori';
        break;
      case 'MEDIUM':
        color = Colors.orange;
        label = "O'rta";
        break;
      case 'URGENT':
        color = Colors.deepOrange;
        label = 'Shoshilinch';
        break;
      default:
        color = Colors.blue;
        label = 'Past';
    }

    return Chip(
      label: Text(label, style: TextStyle(color: color, fontSize: 12)),
      side: BorderSide(color: color),
      backgroundColor: color.withValues(alpha: 0.1),
      padding: EdgeInsets.zero,
      visualDensity: VisualDensity.compact,
    );
  }

  Widget _buildDetailRow(
      BuildContext context, String label, String value, IconData icon) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(icon, size: 20, color: theme.colorScheme.onSurfaceVariant),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
              Text(value, style: theme.textTheme.bodyMedium),
            ],
          ),
        ],
      ),
    );
  }
}
