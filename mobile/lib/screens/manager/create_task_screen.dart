import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../models/auth/user_model.dart';
import '../../providers/auth_provider.dart';
import '../../providers/task_provider.dart';
import '../../widgets/app_background.dart';

final _staffForTaskProvider = FutureProvider<List<User>>((ref) async {
  return ref.read(apiServiceProvider).getStaffList();
});

class CreateTaskScreen extends ConsumerStatefulWidget {
  const CreateTaskScreen({super.key});

  @override
  ConsumerState<CreateTaskScreen> createState() => _CreateTaskScreenState();
}

class _CreateTaskScreenState extends ConsumerState<CreateTaskScreen> {
  final _formKey = GlobalKey<FormState>();
  final _titleController = TextEditingController();
  final _descriptionController = TextEditingController();

  final Set<String> _selectedStaff = {};
  DateTime? _deadline;
  bool _isSubmitting = false;

  @override
  void dispose() {
    _titleController.dispose();
    _descriptionController.dispose();
    super.dispose();
  }

  Future<void> _pickDeadline() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _deadline ?? DateTime.now().add(const Duration(days: 7)),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (picked != null) {
      setState(() => _deadline = picked);
    }
  }

  Future<void> _submit(List<User> allStaff) async {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedStaff.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Kamida bitta xodim tanlang')),
      );
      return;
    }

    setState(() => _isSubmitting = true);

    final taskData = {
      'title': _titleController.text.trim(),
      'description': _descriptionController.text.trim(),
      if (_deadline != null) 'deadline': _deadline!.toIso8601String(),
    };

    bool success;
    if (_selectedStaff.length == 1) {
      success = await ref.read(taskNotifierProvider.notifier).createTask({
        ...taskData,
        'assignedTo': _selectedStaff.first,
      });
    } else {
      success = await ref.read(taskNotifierProvider.notifier).createBulkTasks(
            _selectedStaff.toList(),
            taskData,
          );
    }

    if (mounted) {
      setState(() => _isSubmitting = false);
      if (success) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(_selectedStaff.length > 1
                ? '${_selectedStaff.length} ta xodimga topshiriq yuborildi'
                : 'Topshiriq muvaffaqiyatli yaratildi'),
          ),
        );
        context.pop();
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Topshiriq yaratishda xatolik'),
            backgroundColor: Theme.of(context).colorScheme.error,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final staffAsync = ref.watch(_staffForTaskProvider);
    final theme = Theme.of(context);

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) context.pop();
      },
      child: Scaffold(
        appBar: AppBar(
          leading: BackButton(onPressed: () => context.pop()),
          title: const Text('Topshiriq yaratish'),
        ),
        body: AppBackground(child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextFormField(
                  controller: _titleController,
                  decoration: const InputDecoration(
                    labelText: 'Sarlavha',
                    prefixIcon: Icon(Icons.title),
                  ),
                  validator: (v) => v == null || v.trim().isEmpty
                      ? 'Sarlavha kiritilishi shart'
                      : null,
                ),
                const SizedBox(height: 16),

                TextFormField(
                  controller: _descriptionController,
                  decoration: const InputDecoration(
                    labelText: 'Tavsif',
                    prefixIcon: Icon(Icons.description),
                    alignLabelWithHint: true,
                  ),
                  maxLines: 4,
                  validator: (v) => v == null || v.trim().isEmpty
                      ? 'Tavsif kiritilishi shart'
                      : null,
                ),
                const SizedBox(height: 16),

                // Multi-select staff
                staffAsync.when(
                  loading: () => const LinearProgressIndicator(),
                  error: (_, __) =>
                      const Text('Xodimlar yuklanmadi'),
                  data: (staff) => Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text(
                            'Xodimlar',
                            style: theme.textTheme.titleSmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                          const Spacer(),
                          TextButton.icon(
                            onPressed: () {
                              setState(() {
                                if (_selectedStaff.length == staff.length) {
                                  _selectedStaff.clear();
                                } else {
                                  _selectedStaff
                                      .addAll(staff.map((s) => s.id));
                                }
                              });
                            },
                            icon: Icon(
                              _selectedStaff.length == staff.length
                                  ? Icons.deselect
                                  : Icons.select_all,
                              size: 16,
                            ),
                            label: Text(
                              _selectedStaff.length == staff.length
                                  ? 'Bekor qilish'
                                  : 'Barchasini tanlash',
                              style: const TextStyle(fontSize: 12),
                            ),
                          ),
                        ],
                      ),
                      Container(
                        decoration: BoxDecoration(
                          border: Border.all(
                              color: theme.colorScheme.outlineVariant),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        constraints: const BoxConstraints(maxHeight: 200),
                        child: ListView.builder(
                          shrinkWrap: true,
                          itemCount: staff.length,
                          itemBuilder: (context, index) {
                            final s = staff[index];
                            final selected = _selectedStaff.contains(s.id);
                            return CheckboxListTile(
                              dense: true,
                              title: Text(s.displayName),
                              value: selected,
                              onChanged: (v) {
                                setState(() {
                                  if (v == true) {
                                    _selectedStaff.add(s.id);
                                  } else {
                                    _selectedStaff.remove(s.id);
                                  }
                                });
                              },
                            );
                          },
                        ),
                      ),
                      if (_selectedStaff.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text(
                            '${_selectedStaff.length} ta xodim tanlandi',
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.primary,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                InkWell(
                  onTap: _pickDeadline,
                  child: InputDecorator(
                    decoration: const InputDecoration(
                      labelText: 'Muddat',
                      prefixIcon: Icon(Icons.calendar_today),
                    ),
                    child: Text(
                      _deadline != null
                          ? DateFormat('dd.MM.yyyy').format(_deadline!)
                          : 'Muddat tanlash',
                      style: TextStyle(
                        color: _deadline != null
                            ? theme.colorScheme.onSurface
                            : theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 32),

                staffAsync.maybeWhen(
                  data: (staff) => ElevatedButton(
                    onPressed: _isSubmitting ? null : () => _submit(staff),
                    child: _isSubmitting
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: Colors.white),
                          )
                        : Text(_selectedStaff.length > 1
                            ? 'Topshiriq yuborish (${_selectedStaff.length})'
                            : 'Topshiriq yaratish'),
                  ),
                  orElse: () => const SizedBox.shrink(),
                ),
              ],
            ),
          ),
        )),
      ),
    );
  }
}
