import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../models/user_model.dart';
import '../../providers/auth_provider.dart';
import '../../providers/task_provider.dart';

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

  String? _selectedAssignee;
  String _selectedPriority = 'MEDIUM';
  DateTime? _deadline;
  bool _isSubmitting = false;

  final _priorities = ['LOW', 'MEDIUM', 'HIGH'];

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

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedAssignee == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please select an assignee')),
      );
      return;
    }

    setState(() => _isSubmitting = true);

    final data = {
      'title': _titleController.text.trim(),
      'description': _descriptionController.text.trim(),
      'assignedTo': _selectedAssignee,
      'priority': _selectedPriority,
      if (_deadline != null) 'deadline': _deadline!.toIso8601String(),
    };

    final success =
        await ref.read(taskNotifierProvider.notifier).createTask(data);

    if (mounted) {
      setState(() => _isSubmitting = false);
      if (success) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Task created successfully')),
        );
        context.pop();
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Failed to create task'),
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
        title: const Text('Create Task'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextFormField(
                controller: _titleController,
                decoration: const InputDecoration(
                  labelText: 'Task Title',
                  prefixIcon: Icon(Icons.title),
                ),
                validator: (v) => v == null || v.trim().isEmpty
                    ? 'Title is required'
                    : null,
              ),
              const SizedBox(height: 16),

              TextFormField(
                controller: _descriptionController,
                decoration: const InputDecoration(
                  labelText: 'Description',
                  prefixIcon: Icon(Icons.description),
                  alignLabelWithHint: true,
                ),
                maxLines: 4,
                validator: (v) => v == null || v.trim().isEmpty
                    ? 'Description is required'
                    : null,
              ),
              const SizedBox(height: 16),

              // Assignee dropdown
              staffAsync.when(
                loading: () => const LinearProgressIndicator(),
                error: (_, __) => const Text('Failed to load staff'),
                data: (staff) => DropdownButtonFormField<String>(
                  initialValue: _selectedAssignee,
                  decoration: const InputDecoration(
                    labelText: 'Assign To',
                    prefixIcon: Icon(Icons.person_outline),
                  ),
                  items: staff
                      .map((s) => DropdownMenuItem(
                            value: s.id,
                            child: Text(s.displayName),
                          ))
                      .toList(),
                  onChanged: (v) => setState(() => _selectedAssignee = v),
                  validator: (v) => v == null ? 'Select an assignee' : null,
                ),
              ),
              const SizedBox(height: 16),

              // Priority dropdown
              DropdownButtonFormField<String>(
                initialValue: _selectedPriority,
                decoration: const InputDecoration(
                  labelText: 'Priority',
                  prefixIcon: Icon(Icons.flag_outlined),
                ),
                items: _priorities
                    .map((p) => DropdownMenuItem(
                          value: p,
                          child: Row(
                            children: [
                              Icon(
                                Icons.circle,
                                size: 12,
                                color: p == 'HIGH'
                                    ? Colors.red
                                    : p == 'MEDIUM'
                                        ? Colors.orange
                                        : Colors.blue,
                              ),
                              const SizedBox(width: 8),
                              Text(p),
                            ],
                          ),
                        ))
                    .toList(),
                onChanged: (v) {
                  if (v != null) setState(() => _selectedPriority = v);
                },
              ),
              const SizedBox(height: 16),

              // Deadline picker
              InkWell(
                onTap: _pickDeadline,
                child: InputDecorator(
                  decoration: const InputDecoration(
                    labelText: 'Deadline',
                    prefixIcon: Icon(Icons.calendar_today),
                  ),
                  child: Text(
                    _deadline != null
                        ? DateFormat('MMM d, yyyy').format(_deadline!)
                        : 'Select a deadline',
                    style: TextStyle(
                      color: _deadline != null
                          ? theme.colorScheme.onSurface
                          : theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 32),

              ElevatedButton(
                onPressed: _isSubmitting ? null : _submit,
                child: _isSubmitting
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Text('Create Task'),
              ),
            ],
          ),
        ),
      ),
      ),
    );
  }
}
