export interface User {
  id: string;
  name: string;
  phone: string;
  role: 'MANAGER' | 'STAFF';
  departmentId: string;
  managerId?: string;
  isActive: boolean;
  createdAt: string;
}

export interface Task {
  id: string;
  title: string;
  description: string;
  assignedTo: string;
  assignedBy: string;
  assigneeName?: string;
  departmentId: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
  deadline?: string;
  completedAt?: string;
  createdAt: string;
}

export interface AiRule {
  id: string;
  title: string;
  content: string;
  category: string;
  priority: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface KpiScore {
  id: string;
  staffId: string;
  staffName?: string;
  score: number;
  period: string;
  rank?: number;
  breakdown?: Record<string, number>;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  senderId: string;
  senderName?: string;
  senderRole: 'MANAGER' | 'STAFF' | 'AI';
  content: string;
  timestamp: string;
}

export interface Conversation {
  id: string;
  staffId: string;
  staffName: string;
  managerId: string;
  lastMessage?: string;
  lastMessageAt?: string;
  unreadCount: number;
}

export interface Document {
  id: string;
  name: string;
  fileName: string;
  fileUrl: string;
  fileType: string;
  fileSize: number;
  uploadedBy: string;
  uploadedByName?: string;
  createdAt: string;
}

export interface AiConversation {
  id: string;
  staffId: string;
  departmentId: string;
  title: string;
  messageCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface AiMessage {
  role: "user" | "model";
  parts: { text: string }[];
}

export interface ApiResponse<T> {
  data: T;
  message?: string;
  success: boolean;
}
