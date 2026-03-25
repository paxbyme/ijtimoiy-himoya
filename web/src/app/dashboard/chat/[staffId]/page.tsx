"use client";

import { useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  collection,
  query,
  orderBy,
  onSnapshot,
  addDoc,
  serverTimestamp,
  Timestamp,
} from "firebase/firestore";
import { db } from "@/lib/firebase";
import { useAuth } from "@/hooks/useAuth";
import api from "@/lib/api";
import { User } from "@/types";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Send, ArrowLeft, MessageSquare } from "lucide-react";
import Link from "next/link";
import { format } from "date-fns";
import { cn } from "@/lib/utils";

interface FirestoreMessage {
  id: string;
  senderId: string;
  senderName?: string;
  senderRole: string;
  content: string;
  timestamp: Timestamp | null;
}

export default function ChatStaffPage() {
  const params = useParams();
  const staffId = params.staffId as string;
  const { userData } = useAuth();
  const [messages, setMessages] = useState<FirestoreMessage[]>([]);
  const [newMessage, setNewMessage] = useState("");
  const [sending, setSending] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const { data: staffUser, isLoading: staffLoading } = useQuery({
    queryKey: ["staff-user", staffId],
    queryFn: async () => {
      const res = await api.get(`/users/${staffId}`);
      return (res.data.data || res.data) as User;
    },
  });

  // Listen to Firestore messages in real-time
  useEffect(() => {
    if (!staffId || !userData?.id) return;

    const conversationId = [userData.id, staffId].sort().join("_");
    const messagesRef = collection(db, "conversations", conversationId, "messages");
    const q = query(messagesRef, orderBy("timestamp", "asc"));

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const msgs: FirestoreMessage[] = snapshot.docs.map((doc) => ({
        id: doc.id,
        ...doc.data(),
      })) as FirestoreMessage[];
      setMessages(msgs);
    });

    return () => unsubscribe();
  }, [staffId, userData?.id]);

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMessage.trim() || !userData?.id) return;

    setSending(true);
    try {
      const conversationId = [userData.id, staffId].sort().join("_");
      const messagesRef = collection(db, "conversations", conversationId, "messages");

      await addDoc(messagesRef, {
        senderId: userData.id,
        senderName: userData.name,
        senderRole: "MANAGER",
        content: newMessage.trim(),
        timestamp: serverTimestamp(),
      });

      setNewMessage("");
    } catch (error) {
      console.error("Failed to send message:", error);
    } finally {
      setSending(false);
    }
  };

  if (staffLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <LoadingSpinner text="Loading chat..." />
      </div>
    );
  }

  const staffName = staffUser?.name || "Employee";
  const staffInitials = staffName
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);

  return (
    <div className="flex h-[calc(100vh-8rem)] flex-col rounded-lg border bg-white">
      {/* Chat Header */}
      <div className="flex items-center gap-3 border-b px-4 py-3">
        <Link href="/dashboard/chat">
          <Button variant="ghost" size="icon-sm">
            <ArrowLeft className="h-4 w-4" />
          </Button>
        </Link>
        <Avatar size="sm">
          <AvatarFallback>{staffInitials}</AvatarFallback>
        </Avatar>
        <div>
          <h2 className="font-medium">{staffName}</h2>
          <p className="text-xs text-muted-foreground">
            {staffUser?.phone || ""}
          </p>
        </div>
      </div>

      {/* Messages Area */}
      <ScrollArea className="flex-1 p-4">
        {messages.length === 0 ? (
          <EmptyState
            icon={MessageSquare}
            title="No messages yet"
            description="Start the conversation by sending a message."
            className="py-20"
          />
        ) : (
          <div className="space-y-3">
            {messages.map((message) => {
              const isMe = message.senderId === userData?.id;
              const isAi = message.senderRole === "AI";
              const messageTime = message.timestamp
                ? format(
                    (message.timestamp as Timestamp).toDate(),
                    "h:mm a"
                  )
                : "";

              return (
                <div
                  key={message.id}
                  className={cn(
                    "flex",
                    isMe ? "justify-end" : "justify-start"
                  )}
                >
                  <div
                    className={cn(
                      "max-w-[70%] rounded-2xl px-4 py-2",
                      isMe
                        ? "bg-blue-600 text-white"
                        : isAi
                        ? "bg-purple-50 text-purple-900 border border-purple-200"
                        : "bg-gray-100 text-gray-900"
                    )}
                  >
                    {!isMe && (
                      <p className="text-xs font-medium mb-1 opacity-70">
                        {isAi ? "AI Assistant" : message.senderName || "Staff"}
                      </p>
                    )}
                    <p className="text-sm whitespace-pre-wrap">
                      {message.content}
                    </p>
                    {messageTime && (
                      <p
                        className={cn(
                          "mt-1 text-[10px]",
                          isMe ? "text-blue-200" : "text-gray-400"
                        )}
                      >
                        {messageTime}
                      </p>
                    )}
                  </div>
                </div>
              );
            })}
            <div ref={messagesEndRef} />
          </div>
        )}
      </ScrollArea>

      {/* Message Input */}
      <form
        onSubmit={handleSend}
        className="flex items-center gap-2 border-t p-4"
      >
        <Input
          value={newMessage}
          onChange={(e) => setNewMessage(e.target.value)}
          placeholder="Type a message..."
          className="flex-1"
          disabled={sending}
        />
        <Button
          type="submit"
          size="icon"
          className="bg-blue-600 hover:bg-blue-700"
          disabled={!newMessage.trim() || sending}
        >
          <Send className="h-4 w-4" />
        </Button>
      </form>
    </div>
  );
}
