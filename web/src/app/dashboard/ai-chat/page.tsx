"use client";

import { useState, useRef, useEffect, useCallback } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import api from "@/lib/api";
import { auth } from "@/lib/firebase";
import { AiConversation } from "@/types";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import {
  Bot,
  Send,
  Plus,
  MessageSquare,
  Trash2,
  ThumbsUp,
  ThumbsDown,
  Loader2,
  PanelLeftClose,
  PanelLeft,
} from "lucide-react";

interface ChatMessage {
  role: "user" | "model";
  text: string;
}

interface AiStreamEvent {
  type?: "status" | "meta" | "token" | "replace" | "done" | "error";
  text?: string;
  message?: string;
  conversationId?: string;
}

const MAX_STREAM_ATTEMPTS = 4;
const STREAM_RETRY_BASE_MS = 500;
const STREAM_RETRY_MAX_MS = 4_000;

class StreamRequestError extends Error {
  constructor(message: string, readonly retryable: boolean) {
    super(message);
    this.name = "StreamRequestError";
  }
}

const delay = (milliseconds: number) =>
  new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds));

const assistantDisplayText = (text: string) =>
  text.replace(/\*\*/g, "").replace(/__/g, "").replace(/^#{1,6}\s*/gm, "");

export default function AiChatPage() {
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamStatus, setStreamStatus] = useState("");
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [feedbackGiven, setFeedbackGiven] = useState<Record<number, string>>(
    {}
  );
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const streamAbortRef = useRef<AbortController | null>(null);
  const queryClient = useQueryClient();

  // Fetch conversations list
  const { data: conversations = [] } = useQuery<AiConversation[]>({
    queryKey: ["ai-conversations"],
    queryFn: async () => {
      const res = await api.get("/ai/conversations");
      return res.data.data || res.data;
    },
    staleTime: 30000,
  });

  // Auto-scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // Focus input on load
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  useEffect(() => {
    return () => streamAbortRef.current?.abort();
  }, []);

  const loadConversation = useCallback(async (id: string) => {
    try {
      const res = await api.get(`/ai/conversations/${id}`);
      const convo = res.data.data || res.data;
      setConversationId(id);
      setFeedbackGiven({});
      setMessages(
        (convo.messages || []).map(
          (m: { role: string; parts?: { text: string }[] }) => ({
            role: m.role as "user" | "model",
            text: m.parts?.[0]?.text || "",
          })
        )
      );
    } catch {
      // Failed to load conversation
    }
  }, []);

  const startNewConversation = () => {
    setConversationId(null);
    setMessages([]);
    setFeedbackGiven({});
    setStreamStatus("");
    inputRef.current?.focus();
  };

  const deleteConversation = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await api.delete(`/ai/conversations/${id}`);
      queryClient.invalidateQueries({ queryKey: ["ai-conversations"] });
      if (conversationId === id) {
        startNewConversation();
      }
    } catch {
      // Failed to delete
    }
  };

  const submitFeedback = async (messageIndex: number, rating: string) => {
    if (!conversationId) return;
    try {
      await api.post("/ai/feedback", {
        conversationId,
        messageIndex,
        rating,
      });
      setFeedbackGiven((prev) => ({ ...prev, [messageIndex]: rating }));
    } catch {
      // Failed to submit feedback
    }
  };

  const sendMessage = async () => {
    const text = input.trim();
    if (!text || isStreaming) return;

    setInput("");
    const userMsg: ChatMessage = { role: "user", text };
    setMessages((prev) => [...prev, userMsg]);
    setIsStreaming(true);
    setStreamStatus("Lex.uz'dan amaldagi normativ hujjatlar qidirilmoqda...");

    let activeConversationId = conversationId;
    let fullResponse = "";
    let assistantAdded = false;
    const controller = new AbortController();
    streamAbortRef.current = controller;

    const renderAssistant = (content: string) => {
      if (!assistantAdded) {
        assistantAdded = true;
        setMessages((prev) => [...prev, { role: "model", text: content }]);
        return;
      }

      setMessages((prev) => {
        const next = [...prev];
        const last = next[next.length - 1];
        if (last?.role === "model") {
          next[next.length - 1] = { role: "model", text: content };
        }
        return next;
      });
    };

    try {
      const baseUrl = String(api.defaults.baseURL || "").replace(/\/$/, "");

      for (let attempt = 0; attempt < MAX_STREAM_ATTEMPTS; attempt += 1) {
        if (attempt > 0) {
          const retryDelay = Math.min(
            STREAM_RETRY_BASE_MS * 2 ** (attempt - 1),
            STREAM_RETRY_MAX_MS
          );
          setStreamStatus(
            `Ulanish uzildi. ${Math.ceil(retryDelay / 1000)} soniyada qayta ulanmoqda...`
          );
          await delay(retryDelay);
          fullResponse = "";
          if (assistantAdded) renderAssistant("");
        }

        try {
          const token = await auth.currentUser?.getIdToken();
          const response = await fetch(`${baseUrl}/ai/chat/stream`, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
            body: JSON.stringify({
              message: text,
              conversationId: activeConversationId,
            }),
            signal: controller.signal,
          });

          if (response.status === 401) {
            window.location.href = "/login";
            throw new StreamRequestError("Avtorizatsiya muddati tugadi", false);
          }
          if (!response.ok || !response.body) {
            const retryable =
              response.status === 408 ||
              response.status === 425 ||
              response.status === 429 ||
              response.status >= 500;
            throw new StreamRequestError(
              `AI so'rovi bajarilmadi (${response.status})`,
              retryable
            );
          }

          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";
          let streamCompleted = false;

          const processEvent = (rawEvent: string) => {
            for (const line of rawEvent.split(/\r?\n/)) {
              if (!line.startsWith("data:")) continue;
              const raw = line.slice(5).trim();
              if (!raw) continue;

              const event = JSON.parse(raw) as AiStreamEvent;
              if (event.type === "status") {
                setStreamStatus(event.message || "");
              } else if (event.type === "meta") {
                if (event.conversationId) {
                  activeConversationId = event.conversationId;
                  setConversationId(event.conversationId);
                }
              } else if (event.type === "token") {
                const chunk = event.text || "";
                if (!chunk) continue;
                fullResponse += chunk;
                setStreamStatus("");
                renderAssistant(fullResponse);
              } else if (event.type === "replace") {
                // The backend re-formatted the answer after streaming it.
                // Swap the rendered draft for the corrected text.
                const corrected = event.text || "";
                if (!corrected) continue;
                fullResponse = corrected;
                setStreamStatus("");
                renderAssistant(fullResponse);
              } else if (event.type === "done") {
                streamCompleted = true;
              } else if (event.type === "error") {
                throw new StreamRequestError(
                  event.message || "AI javobida xatolik yuz berdi",
                  false
                );
              }
            }
          };

          while (true) {
            const { value, done } = await reader.read();
            buffer += decoder.decode(value || new Uint8Array(), {
              stream: !done,
            });
            const events = buffer.split(/\r?\n\r?\n/);
            buffer = events.pop() || "";
            events.forEach(processEvent);
            if (done) break;
          }
          if (buffer.trim()) processEvent(buffer);

          if (!streamCompleted) {
            throw new StreamRequestError(
              "AI oqimi javob tugashidan oldin uzildi",
              true
            );
          }
          if (!fullResponse) {
            throw new StreamRequestError("AI bo'sh javob qaytardi", false);
          }
          break;
        } catch (error) {
          if (error instanceof DOMException && error.name === "AbortError") {
            throw error;
          }

          const retryable =
            error instanceof StreamRequestError
              ? error.retryable
              : error instanceof TypeError;
          const isLastAttempt = attempt === MAX_STREAM_ATTEMPTS - 1;
          if (!retryable || isLastAttempt) throw error;
        }
      }

      queryClient.invalidateQueries({ queryKey: ["ai-conversations"] });
    } catch (err) {
      if (err instanceof DOMException && err.name === "AbortError") return;
      const errorMsg =
        err instanceof Error ? err.message : String(err);
      renderAssistant(`Xatolik: ${errorMsg}`);
    } finally {
      if (streamAbortRef.current === controller) {
        streamAbortRef.current = null;
      }
      setIsStreaming(false);
      setStreamStatus("");
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  return (
    <div className="flex h-[calc(100vh-7.5rem)] overflow-hidden rounded-lg border bg-white">
      {/* Conversation Sidebar */}
      {sidebarOpen && (
        <div className="flex w-72 flex-col border-r bg-gray-50/50">
          <div className="flex items-center justify-between border-b p-3">
            <h3 className="text-sm font-semibold">Suhbatlar</h3>
            <div className="flex gap-1">
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={startNewConversation}
                title="New conversation"
              >
                <Plus className="h-4 w-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={() => setSidebarOpen(false)}
              >
                <PanelLeftClose className="h-4 w-4" />
              </Button>
            </div>
          </div>
          <ScrollArea className="flex-1">
            <div className="flex flex-col gap-0.5 p-2">
              {conversations.length === 0 && (
                <p className="px-3 py-8 text-center text-xs text-muted-foreground">
                  Hozircha suhbatlar yo&apos;q.
                </p>
              )}
              {conversations.map((convo) => (
                <button
                  key={convo.id}
                  onClick={() => loadConversation(convo.id)}
                  className={cn(
                    "group flex items-center gap-2 rounded-md px-3 py-2 text-left text-sm transition-colors",
                    conversationId === convo.id
                      ? "bg-blue-50 text-blue-700"
                      : "text-gray-600 hover:bg-gray-100"
                  )}
                >
                  <MessageSquare className="h-3.5 w-3.5 shrink-0" />
                  <span className="flex-1 truncate">
                    {convo.title || "Nomsiz suhbat"}
                  </span>
                  <Badge variant="secondary" className="text-[10px] px-1.5">
                    {convo.messageCount}
                  </Badge>
                  <button
                    onClick={(e) => deleteConversation(convo.id, e)}
                    className="hidden shrink-0 rounded p-0.5 text-gray-400 hover:bg-red-50 hover:text-red-500 group-hover:block"
                  >
                    <Trash2 className="h-3 w-3" />
                  </button>
                </button>
              ))}
            </div>
          </ScrollArea>
        </div>
      )}

      {/* Chat Area */}
      <div className="flex flex-1 flex-col">
        {/* Chat Header */}
        <div className="flex items-center gap-2 border-b px-4 py-3">
          {!sidebarOpen && (
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={() => setSidebarOpen(true)}
            >
              <PanelLeft className="h-4 w-4" />
            </Button>
          )}
          <Bot className="h-5 w-5 text-blue-600" />
          <h2 className="text-sm font-semibold">IHMA Bosh AI yordamchisi</h2>
          {conversationId && (
            <Badge variant="outline" className="ml-auto text-xs">
              {messages.length} messages
            </Badge>
          )}
        </div>

        {/* Messages */}
        <ScrollArea className="flex-1 px-4">
          {messages.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center py-20">
              <div className="mb-4 rounded-full bg-blue-50 p-4">
                <Bot className="h-10 w-10 text-blue-600" />
              </div>
              <h3 className="mb-2 text-lg font-semibold">
                IHMA Bosh AI yordamchisi
              </h3>
              <p className="max-w-md text-center text-sm text-muted-foreground">
                Fuqaro holatini yozing. Yordamchi amaldagi bazadagi normativ
                hujjatlarga tayangan holda kompleks yo&apos;l xaritasini tuzadi.
              </p>
            </div>
          ) : (
            <div className="flex flex-col gap-4 py-4">
              {messages.map((msg, idx) => (
                <div
                  key={idx}
                  className={cn(
                    "flex gap-3",
                    msg.role === "user" ? "justify-end" : "justify-start"
                  )}
                >
                  {msg.role === "model" && (
                    <div className="mt-1 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-100">
                      <Bot className="h-4 w-4 text-blue-600" />
                    </div>
                  )}
                  <div
                    className={cn(
                      "max-w-[85%] rounded-xl px-4 py-2.5",
                      msg.role === "user"
                        ? "bg-blue-600 text-white"
                        : "bg-gray-100 text-gray-900"
                    )}
                  >
                    <p className="whitespace-pre-wrap text-sm leading-relaxed">
                      {msg.role === "model"
                        ? assistantDisplayText(msg.text)
                        : msg.text}
                      {isStreaming &&
                        idx === messages.length - 1 &&
                        msg.role === "model" && (
                          <span className="ml-1 inline-block h-4 w-1 animate-pulse bg-gray-400" />
                        )}
                    </p>
                    {/* Feedback buttons for AI messages */}
                    {msg.role === "model" &&
                      !isStreaming &&
                      msg.text &&
                      conversationId && (
                        <div className="mt-2 flex gap-1 border-t border-gray-200 pt-2">
                          <button
                            onClick={() => submitFeedback(idx, "up")}
                            className={cn(
                              "rounded p-1 transition-colors",
                              feedbackGiven[idx] === "up"
                                ? "bg-green-100 text-green-600"
                                : "text-gray-400 hover:bg-gray-200 hover:text-gray-600"
                            )}
                            disabled={!!feedbackGiven[idx]}
                          >
                            <ThumbsUp className="h-3.5 w-3.5" />
                          </button>
                          <button
                            onClick={() => submitFeedback(idx, "down")}
                            className={cn(
                              "rounded p-1 transition-colors",
                              feedbackGiven[idx] === "down"
                                ? "bg-red-100 text-red-600"
                                : "text-gray-400 hover:bg-gray-200 hover:text-gray-600"
                            )}
                            disabled={!!feedbackGiven[idx]}
                          >
                            <ThumbsDown className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      )}
                  </div>
                </div>
              ))}
              {isStreaming && messages[messages.length - 1]?.role === "user" && (
                <div className="flex gap-3">
                  <div className="mt-1 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-100">
                    <Bot className="h-4 w-4 text-blue-600" />
                  </div>
                  <div className="flex items-center gap-2 rounded-xl bg-gray-100 px-4 py-2.5">
                    <Loader2 className="h-4 w-4 animate-spin text-blue-600" />
                    <span className="text-sm text-gray-500">
                      {streamStatus || "Javob tayyorlanmoqda..."}
                    </span>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>
          )}
        </ScrollArea>

        {/* Input Area */}
        <div className="border-t p-4">
          <div className="flex items-end gap-2">
            <textarea
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Fuqaro holatini yozing..."
              className="max-h-32 min-h-[2.5rem] flex-1 resize-none rounded-lg border bg-gray-50 px-4 py-2.5 text-sm focus:border-blue-300 focus:outline-none focus:ring-1 focus:ring-blue-300"
              rows={1}
              disabled={isStreaming}
            />
            <Button
              onClick={sendMessage}
              disabled={!input.trim() || isStreaming}
              size="icon"
              className="h-10 w-10 shrink-0"
            >
              {isStreaming ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Send className="h-4 w-4" />
              )}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
