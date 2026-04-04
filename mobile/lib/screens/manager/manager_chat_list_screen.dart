import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../providers/auth_provider.dart';
import '../../providers/chat_provider.dart';
import '../../widgets/loading_widget.dart';
import '../../widgets/empty_state_widget.dart';
import 'employee_list_screen.dart' show staffListProvider;

class ManagerChatListScreen extends ConsumerWidget {
  const ManagerChatListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userProfile = ref.watch(userProfileProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Messages'),
      ),
      body: userProfile.when(
        loading: () => const LoadingWidget(),
        error: (_, __) => const Center(child: Text('Error loading profile')),
        data: (user) {
          if (user == null) {
            return const Center(child: Text('Not logged in'));
          }

          final conversationsAsync =
              ref.watch(conversationsProvider(user.id));
          final staffAsync = ref.watch(staffListProvider);
          final staffMap = staffAsync.maybeWhen(
            data: (list) => {for (final s in list) s.id: s.displayName},
            orElse: () => <String, String>{},
          );

          return conversationsAsync.when(
            loading: () => const LoadingWidget(),
            error: (_, __) =>
                const Center(child: Text('Error loading conversations')),
            data: (conversations) {
              if (conversations.isEmpty) {
                return const EmptyStateWidget(
                  icon: Icons.chat_outlined,
                  message: 'No conversations yet.',
                );
              }

              return ListView.builder(
                padding: const EdgeInsets.all(8),
                itemCount: conversations.length,
                itemBuilder: (context, index) {
                  final conv = conversations[index];
                  final otherParticipant = conv.participants.firstWhere(
                    (p) => p != user.id,
                    orElse: () => 'Unknown',
                  );
                  final displayName =
                      staffMap[otherParticipant] ?? otherParticipant;
                  final unread = conv.unreadCount[user.id] ?? 0;

                  return Card(
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor: theme.colorScheme.primaryContainer,
                        child: Icon(
                          Icons.person,
                          color: theme.colorScheme.onPrimaryContainer,
                        ),
                      ),
                      title: Text(
                        displayName,
                        style: TextStyle(
                          fontWeight: unread > 0
                              ? FontWeight.bold
                              : FontWeight.normal,
                        ),
                      ),
                      subtitle: conv.lastMessage != null
                          ? Text(
                              conv.lastMessage!,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontWeight: unread > 0
                                    ? FontWeight.w600
                                    : FontWeight.normal,
                              ),
                            )
                          : null,
                      trailing: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          if (conv.lastMessageAt != null)
                            Text(
                              _formatTime(conv.lastMessageAt!),
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          if (unread > 0) ...[
                            const SizedBox(height: 4),
                            CircleAvatar(
                              radius: 10,
                              backgroundColor: theme.colorScheme.primary,
                              child: Text(
                                '$unread',
                                style: TextStyle(
                                  fontSize: 10,
                                  color: theme.colorScheme.onPrimary,
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                      onTap: () =>
                          context.push('/manager/chat/$otherParticipant'),
                    ),
                  );
                },
              );
            },
          );
        },
      ),
    );
  }

  String _formatTime(DateTime dateTime) {
    final now = DateTime.now();
    final diff = now.difference(dateTime);

    if (diff.inDays == 0) {
      return DateFormat('HH:mm').format(dateTime);
    } else if (diff.inDays == 1) {
      return 'Yesterday';
    } else if (diff.inDays < 7) {
      return DateFormat('EEE').format(dateTime);
    }
    return DateFormat('MMM d').format(dateTime);
  }
}
