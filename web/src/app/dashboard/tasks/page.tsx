"use client";

import { useState, useRef } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm, Controller } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import api from "@/lib/api";
import { Task, User } from "@/types";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
  DialogClose,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Plus,
  CheckSquare,
  Loader2,
  Paperclip,
  Download,
  CheckCircle2,
  Clock,
  AlertCircle,
  Users,
} from "lucide-react";
import { format } from "date-fns";

const createTaskSchema = z.object({
  title: z.string().min(2, "Sarlavha kiritilishi shart"),
  description: z.string().min(1, "Tavsif kiritilishi shart"),
  priority: z.enum(["LOW", "MEDIUM", "HIGH", "URGENT"]),
  deadline: z.string().optional(),
});

type CreateTaskData = z.infer<typeof createTaskSchema>;

const priorityColors: Record<string, string> = {
  LOW: "bg-gray-100 text-gray-700",
  MEDIUM: "bg-blue-100 text-blue-700",
  HIGH: "bg-orange-100 text-orange-700",
  URGENT: "bg-red-100 text-red-700",
};

const priorityLabels: Record<string, string> = {
  LOW: "Past",
  MEDIUM: "O'rta",
  HIGH: "Yuqori",
  URGENT: "Shoshilinch",
};

const statusColors: Record<string, string> = {
  NEW: "bg-yellow-100 text-yellow-700",
  IN_PROGRESS: "bg-blue-100 text-blue-700",
  COMPLETED: "bg-green-100 text-green-700",
  CANCELLED: "bg-gray-100 text-gray-700",
  OVERDUE: "bg-red-100 text-red-700",
};

const statusLabels: Record<string, string> = {
  NEW: "Yangi",
  IN_PROGRESS: "Jarayonda",
  COMPLETED: "Bajarildi",
  CANCELLED: "Bekor",
  OVERDUE: "Muddati o'tgan",
};

type TabType = "ALL" | "NEW" | "IN_PROGRESS" | "OVERDUE" | "COMPLETED";

function isOverdue(task: Task): boolean {
  if (!task.deadline) return false;
  if (task.status === "COMPLETED" || task.status === "CANCELLED") return false;
  return new Date(task.deadline) < new Date();
}

export default function TasksPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<TabType>("ALL");
  const [selectedStaff, setSelectedStaff] = useState<string[]>([]);
  const [uploadingTaskId, setUploadingTaskId] = useState<string | null>(null);
  const [viewingTask, setViewingTask] = useState<Task | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();

  const { data: tasks, isLoading: tasksLoading } = useQuery({
    queryKey: ["tasks"],
    queryFn: async () => {
      const res = await api.get("/tasks");
      const data = res.data.data || res.data;
      return (Array.isArray(data) ? data : data?.content ?? []) as Task[];
    },
  });

  const { data: staff } = useQuery({
    queryKey: ["staff"],
    queryFn: async () => {
      const res = await api.get("/users/staff");
      const data = res.data.data || res.data;
      return (Array.isArray(data) ? data : data?.content ?? []) as User[];
    },
  });

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<CreateTaskData>({
    resolver: zodResolver(createTaskSchema),
    defaultValues: { priority: "MEDIUM" },
  });

  const createMutation = useMutation({
    mutationFn: async (data: CreateTaskData) => {
      if (selectedStaff.length > 1) {
        const res = await api.post("/tasks/bulk", {
          assignedToList: selectedStaff,
          title: data.title,
          description: data.description,
          priority: data.priority,
          deadline: data.deadline,
        });
        return res.data;
      } else {
        const res = await api.post("/tasks", {
          title: data.title,
          description: data.description,
          assignedTo: selectedStaff[0],
          priority: data.priority,
          deadline: data.deadline,
        });
        return res.data;
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      toast.success(
        selectedStaff.length > 1
          ? `${selectedStaff.length} ta xodimga topshiriq yuborildi`
          : "Topshiriq muvaffaqiyatli yaratildi"
      );
      setDialogOpen(false);
      setSelectedStaff([]);
      reset();
    },
    onError: () => {
      toast.error("Topshiriq yaratishda xatolik");
    },
  });

  const acceptMutation = useMutation({
    mutationFn: async (taskId: string) => {
      const res = await api.put(`/tasks/${taskId}/accept`);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      toast.success("Topshiriq qabul qilindi");
      setViewingTask(null);
    },
    onError: () => {
      toast.error("Qabul qilishda xatolik");
    },
  });

  const uploadAttachment = async (taskId: string, file: File) => {
    setUploadingTaskId(taskId);
    const formData = new FormData();
    formData.append("file", file);
    try {
      await api.post(`/tasks/${taskId}/attachment`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      toast.success("Fayl muvaffaqiyatli yuklandi");
    } catch {
      toast.error("Fayl yuklashda xatolik");
    } finally {
      setUploadingTaskId(null);
    }
  };

  const onSubmit = (data: CreateTaskData) => {
    if (selectedStaff.length === 0) {
      toast.error("Kamida bitta xodim tanlang");
      return;
    }
    createMutation.mutate(data);
  };

  const toggleStaff = (id: string) => {
    setSelectedStaff((prev) =>
      prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id]
    );
  };

  const toggleAll = () => {
    const allIds = (staff || []).map((s) => s.id);
    setSelectedStaff((prev) =>
      prev.length === allIds.length ? [] : allIds
    );
  };

  if (tasksLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <LoadingSpinner text="Topshiriqlar yuklanmoqda..." />
      </div>
    );
  }

  const allTasks = tasks || [];
  const overdueTasks = allTasks.filter(isOverdue);

  const filteredTasks =
    activeTab === "ALL"
      ? allTasks
      : activeTab === "OVERDUE"
      ? overdueTasks
      : allTasks.filter((t) => t.status === activeTab);

  const tabs: { key: TabType; label: string; icon: React.ReactNode; count: number }[] = [
    { key: "ALL", label: "Hammasi", icon: null, count: allTasks.length },
    {
      key: "NEW",
      label: "Yangi",
      icon: <Clock className="h-3.5 w-3.5" />,
      count: allTasks.filter((t) => t.status === "NEW").length,
    },
    {
      key: "IN_PROGRESS",
      label: "Jarayonda",
      icon: <Loader2 className="h-3.5 w-3.5" />,
      count: allTasks.filter((t) => t.status === "IN_PROGRESS").length,
    },
    {
      key: "OVERDUE",
      label: "Muddati o'tgan",
      icon: <AlertCircle className="h-3.5 w-3.5" />,
      count: overdueTasks.length,
    },
    {
      key: "COMPLETED",
      label: "Bajarildi",
      icon: <CheckCircle2 className="h-3.5 w-3.5" />,
      count: allTasks.filter((t) => t.status === "COMPLETED").length,
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Topshiriqlar</h1>
          <p className="text-sm text-muted-foreground">
            Jamoa topshiriqlarini boshqarish
          </p>
        </div>
        <Button
          className="bg-blue-600 hover:bg-blue-700"
          onClick={() => {
            setSelectedStaff([]);
            reset();
            setDialogOpen(true);
          }}
        >
          <Plus className="mr-2 h-4 w-4" />
          Topshiriq yaratish
        </Button>
      </div>

      {/* Tabs */}
      <div className="flex flex-wrap gap-2">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              activeTab === tab.key
                ? tab.key === "OVERDUE"
                  ? "bg-red-600 text-white"
                  : "bg-blue-600 text-white"
                : "bg-white border text-muted-foreground hover:bg-slate-50"
            }`}
          >
            {tab.icon}
            {tab.label}
            <span
              className={`ml-0.5 rounded-full px-1.5 py-0.5 text-xs ${
                activeTab === tab.key
                  ? "bg-white/20 text-white"
                  : "bg-slate-100 text-slate-600"
              }`}
            >
              {tab.count}
            </span>
          </button>
        ))}
      </div>

      {/* Tasks Table */}
      {filteredTasks.length === 0 ? (
        <EmptyState
          icon={CheckSquare}
          title="Topshiriq topilmadi"
          description={
            activeTab === "ALL"
              ? "Birinchi topshiriqni yarating."
              : `${tabs.find((t) => t.key === activeTab)?.label} topshiriqlari yo'q.`
          }
        />
      ) : (
        <div className="rounded-lg border bg-white">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Sarlavha</TableHead>
                <TableHead>Xodim</TableHead>
                <TableHead>Ustuvorlik</TableHead>
                <TableHead>Holat</TableHead>
                <TableHead>Muddat</TableHead>
                <TableHead>Fayl</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredTasks.map((task) => {
                const overdue = isOverdue(task);
                return (
                  <TableRow
                    key={task.id}
                    className="cursor-pointer hover:bg-slate-50"
                    onClick={() => setViewingTask(task)}
                  >
                    <TableCell>
                      <div>
                        <p className="font-medium">{task.title}</p>
                        <p className="text-xs text-muted-foreground line-clamp-1">
                          {task.description}
                        </p>
                      </div>
                    </TableCell>
                    <TableCell>{task.assigneeName || "—"}</TableCell>
                    <TableCell>
                      <Badge
                        variant="secondary"
                        className={priorityColors[task.priority]}
                      >
                        {priorityLabels[task.priority] || task.priority}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant="secondary"
                        className={
                          overdue
                            ? statusColors.OVERDUE
                            : statusColors[task.status]
                        }
                      >
                        {overdue
                          ? statusLabels.OVERDUE
                          : statusLabels[task.status] || task.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {task.deadline
                        ? format(new Date(task.deadline), "dd.MM.yyyy")
                        : "—"}
                    </TableCell>
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      {task.attachmentUrl ? (
                        <a
                          href={task.attachmentUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1 text-blue-600 hover:underline text-xs"
                        >
                          <Paperclip className="h-3 w-3" />
                          {task.attachmentName || "Fayl"}
                        </a>
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}

      {/* Create Task Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Yangi topshiriq yaratish</DialogTitle>
            <DialogDescription>
              Bir yoki bir nechta xodimga topshiriq bering.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="title">Sarlavha</Label>
              <Input id="title" placeholder="Topshiriq sarlavhasi" {...register("title")} />
              {errors.title && (
                <p className="text-xs text-red-500">{errors.title.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">Tavsif</Label>
              <Textarea
                id="description"
                placeholder="Topshiriq tavsifi..."
                {...register("description")}
              />
              {errors.description && (
                <p className="text-xs text-red-500">{errors.description.message}</p>
              )}
            </div>

            {/* Staff multi-select */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>Xodimlar</Label>
                <button
                  type="button"
                  onClick={toggleAll}
                  className="flex items-center gap-1 text-xs text-blue-600 hover:underline"
                >
                  <Users className="h-3 w-3" />
                  {selectedStaff.length === (staff || []).length
                    ? "Barchasini bekor qilish"
                    : "Barchasini tanlash"}
                </button>
              </div>
              <div className="max-h-40 overflow-y-auto rounded-md border p-2 space-y-1">
                {(staff || []).length === 0 ? (
                  <p className="text-xs text-muted-foreground text-center py-2">
                    Xodimlar topilmadi
                  </p>
                ) : (
                  (staff || []).map((s) => (
                    <label
                      key={s.id}
                      className="flex items-center gap-2 rounded px-2 py-1.5 hover:bg-slate-50 cursor-pointer"
                    >
                      <Checkbox
                        checked={selectedStaff.includes(s.id)}
                        onCheckedChange={() => toggleStaff(s.id)}
                      />
                      <span className="text-sm">{s.name}</span>
                    </label>
                  ))
                )}
              </div>
              {selectedStaff.length > 0 && (
                <p className="text-xs text-blue-600">
                  {selectedStaff.length} ta xodim tanlandi
                </p>
              )}
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Ustuvorlik</Label>
                <Controller
                  name="priority"
                  control={control}
                  render={({ field }) => (
                    <Select
                      value={field.value}
                      onValueChange={(v) => { if (v) field.onChange(v); }}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder="Tanlang" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="LOW">Past</SelectItem>
                        <SelectItem value="MEDIUM">O&#39;rta</SelectItem>
                        <SelectItem value="HIGH">Yuqori</SelectItem>
                        <SelectItem value="URGENT">Shoshilinch</SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="deadline">Muddat</Label>
                <Input id="deadline" type="date" {...register("deadline")} />
              </div>
            </div>

            <DialogFooter>
              <DialogClose render={<Button variant="outline" />}>
                Bekor
              </DialogClose>
              <Button
                type="submit"
                className="bg-blue-600 hover:bg-blue-700"
                disabled={createMutation.isPending}
              >
                {createMutation.isPending ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Yaratilmoqda...
                  </>
                ) : (
                  `Topshiriq yuborish${selectedStaff.length > 1 ? ` (${selectedStaff.length})` : ""}`
                )}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Task Detail / Accept Dialog */}
      {viewingTask && (
        <Dialog open={!!viewingTask} onOpenChange={() => setViewingTask(null)}>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>{viewingTask.title}</DialogTitle>
              <DialogDescription>{viewingTask.description}</DialogDescription>
            </DialogHeader>
            <div className="space-y-3">
              <div className="flex gap-3 flex-wrap">
                <Badge
                  variant="secondary"
                  className={priorityColors[viewingTask.priority]}
                >
                  {priorityLabels[viewingTask.priority]}
                </Badge>
                <Badge
                  variant="secondary"
                  className={
                    isOverdue(viewingTask)
                      ? statusColors.OVERDUE
                      : statusColors[viewingTask.status]
                  }
                >
                  {isOverdue(viewingTask)
                    ? statusLabels.OVERDUE
                    : statusLabels[viewingTask.status]}
                </Badge>
                {viewingTask.managerAccepted && (
                  <Badge variant="secondary" className="bg-green-100 text-green-700">
                    <CheckCircle2 className="mr-1 h-3 w-3" />
                    Qabul qilingan
                  </Badge>
                )}
              </div>

              {viewingTask.deadline && (
                <p className="text-sm text-muted-foreground">
                  Muddat:{" "}
                  <span className="font-medium">
                    {format(new Date(viewingTask.deadline), "dd.MM.yyyy")}
                  </span>
                </p>
              )}

              {viewingTask.assigneeName && (
                <p className="text-sm text-muted-foreground">
                  Xodim:{" "}
                  <span className="font-medium">{viewingTask.assigneeName}</span>
                </p>
              )}

              {/* Attachment section */}
              <div className="rounded-lg border p-3 space-y-2">
                <p className="text-sm font-medium">Fayl</p>
                {viewingTask.attachmentUrl ? (
                  <a
                    href={viewingTask.attachmentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-2 text-sm text-blue-600 hover:underline"
                  >
                    <Download className="h-4 w-4" />
                    {viewingTask.attachmentName || "Faylni yuklab olish"}
                  </a>
                ) : (
                  <p className="text-sm text-muted-foreground">
                    Fayl yuklanmagan
                  </p>
                )}
              </div>
            </div>

            <DialogFooter className="flex-col gap-2 sm:flex-row">
              <DialogClose render={<Button variant="outline" className="w-full sm:w-auto" />}>
                Yopish
              </DialogClose>
              {viewingTask.status === "COMPLETED" &&
                viewingTask.attachmentUrl &&
                !viewingTask.managerAccepted && (
                  <Button
                    className="bg-green-600 hover:bg-green-700 w-full sm:w-auto"
                    onClick={() => acceptMutation.mutate(viewingTask.id)}
                    disabled={acceptMutation.isPending}
                  >
                    {acceptMutation.isPending ? (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    ) : (
                      <CheckCircle2 className="mr-2 h-4 w-4" />
                    )}
                    Qabul qildim
                  </Button>
                )}
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}

      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file && uploadingTaskId) {
            uploadAttachment(uploadingTaskId, file);
          }
          e.target.value = "";
        }}
      />
    </div>
  );
}
