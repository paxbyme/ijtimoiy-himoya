"use client";

import { useState, useRef } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import api from "@/lib/api";
import { AiRule } from "@/types";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
  DialogTrigger,
  DialogClose,
} from "@/components/ui/dialog";
import { Plus, Brain, Pencil, Trash2, Loader2, ToggleLeft, ToggleRight, Upload, FileText } from "lucide-react";

const ruleSchema = z.object({
  title: z.string().min(2, "Sarlavha kiritilishi shart"),
  content: z.string().min(1, "Kontent kiritilishi shart"),
  category: z.string().min(1, "Kategoriya kiritilishi shart"),
  priority: z.number().min(1).max(10),
  active: z.boolean(),
});

type RuleFormData = z.infer<typeof ruleSchema>;

export default function AiRulesPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [uploadDialogOpen, setUploadDialogOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<AiRule | null>(null);
  const [uploadTitle, setUploadTitle] = useState("");
  const [uploadCategory, setUploadCategory] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();

  const { data: rules, isLoading } = useQuery({
    queryKey: ["ai-rules"],
    queryFn: async () => {
      const res = await api.get("/ai-rules");
      return (res.data.data || res.data) as AiRule[];
    },
  });

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors },
  } = useForm<RuleFormData>({
    resolver: zodResolver(ruleSchema),
    defaultValues: { priority: 5, active: true },
  });

  const createMutation = useMutation({
    mutationFn: async (data: RuleFormData) => {
      if (editingRule) {
        const res = await api.put(`/ai-rules/${editingRule.id}`, data);
        return res.data;
      }
      const res = await api.post("/ai-rules", data);
      return res.data;
    },
    onSuccess: (responseData) => {
      const saved: AiRule = responseData.data || responseData;
      const editingId = editingRule?.id;
      queryClient.setQueryData<AiRule[]>(["ai-rules"], (old) => {
        if (editingId) {
          return old?.map((r) => (r.id === editingId ? saved : r)) ?? [saved];
        }
        return [...(old ?? []), saved];
      });
      queryClient.invalidateQueries({ queryKey: ["ai-rules"] });
      toast.success(editingId ? "Qoida yangilandi" : "Qoida yaratildi");
      setDialogOpen(false);
      setEditingRule(null);
      reset();
    },
    onError: () => toast.error("Qoida saqlashda xatolik"),
  });

  const uploadMutation = useMutation({
    mutationFn: async () => {
      if (!selectedFile) throw new Error("Fayl tanlanmagan");
      const formData = new FormData();
      formData.append("file", selectedFile);
      if (uploadTitle) formData.append("title", uploadTitle);
      if (uploadCategory) formData.append("category", uploadCategory);
      const res = await api.post("/ai-rules/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      return res.data;
    },
    onSuccess: (responseData) => {
      const saved: AiRule = responseData.data || responseData;
      queryClient.setQueryData<AiRule[]>(["ai-rules"], (old) => [...(old ?? []), saved]);
      queryClient.invalidateQueries({ queryKey: ["ai-rules"] });
      toast.success("Fayl o'qildi va qoida yaratildi");
      setUploadDialogOpen(false);
      setSelectedFile(null);
      setUploadTitle("");
      setUploadCategory("");
    },
    onError: () => toast.error("Fayldan qoida yaratishda xatolik"),
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => { await api.delete(`/ai-rules/${id}`); },
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: ["ai-rules"] });
      const previous = queryClient.getQueryData<AiRule[]>(["ai-rules"]);
      queryClient.setQueryData<AiRule[]>(["ai-rules"], (old) => old?.filter((r) => r.id !== id) ?? []);
      return { previous };
    },
    onSuccess: () => toast.success("Qoida o'chirildi"),
    onError: (_err, _id, context) => {
      if (context?.previous) queryClient.setQueryData(["ai-rules"], context.previous);
      toast.error("O'chirishda xatolik");
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["ai-rules"] }),
  });

  const toggleMutation = useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => {
      const res = await api.put(`/ai-rules/${id}`, { isActive: active });
      return res.data;
    },
    onMutate: async ({ id, active }) => {
      await queryClient.cancelQueries({ queryKey: ["ai-rules"] });
      const previous = queryClient.getQueryData<AiRule[]>(["ai-rules"]);
      queryClient.setQueryData<AiRule[]>(["ai-rules"], (old) =>
        old?.map((r) => (r.id === id ? { ...r, active } : r)) ?? []
      );
      return { previous };
    },
    onSuccess: () => toast.success("Holat yangilandi"),
    onError: (_err, _vars, context) => {
      if (context?.previous) queryClient.setQueryData(["ai-rules"], context.previous);
      toast.error("Holatni o'zgartirishda xatolik");
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["ai-rules"] }),
  });

  const openEdit = (rule: AiRule) => {
    setEditingRule(rule);
    setValue("title", rule.title);
    setValue("content", rule.content);
    setValue("category", rule.category);
    setValue("priority", rule.priority);
    setValue("active", rule.active);
    setDialogOpen(true);
  };

  const openCreate = () => {
    setEditingRule(null);
    reset({ title: "", content: "", category: "", priority: 5, active: true });
    setDialogOpen(true);
  };

  if (isLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <LoadingSpinner text="AI qoidalari yuklanmoqda..." />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">AI Qoidalari</h1>
          <p className="text-sm text-muted-foreground">
            AI yordamchisi xulq-atvorini boshqarish qoidalari
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            onClick={() => setUploadDialogOpen(true)}
          >
            <Upload className="mr-2 h-4 w-4" />
            Fayldan yuklash
          </Button>
          <Dialog open={dialogOpen} onOpenChange={(open) => {
            setDialogOpen(open);
            if (!open) { setEditingRule(null); reset(); }
          }}>
            <DialogTrigger render={<Button className="bg-blue-600 hover:bg-blue-700" onClick={openCreate} />}>
              <Plus className="mr-2 h-4 w-4" />
              Qoida qo&#39;shish
            </DialogTrigger>
            <DialogContent className="sm:max-w-md">
              <DialogHeader>
                <DialogTitle>{editingRule ? "Qoidani tahrirlash" : "Yangi qoida qo'shish"}</DialogTitle>
                <DialogDescription>
                  {editingRule ? "AI qoidasini yangilang." : "AI yordamchisi uchun yangi qoida kiriting."}
                </DialogDescription>
              </DialogHeader>
              <form onSubmit={handleSubmit((d) => createMutation.mutate(d))} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="title">Sarlavha</Label>
                  <Input id="title" placeholder="Qoida sarlavhasi" {...register("title")} />
                  {errors.title && <p className="text-xs text-red-500">{errors.title.message}</p>}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="content">Kontent</Label>
                  <Textarea id="content" placeholder="Qoidani tasvirlab bering..." rows={4} {...register("content")} />
                  {errors.content && <p className="text-xs text-red-500">{errors.content.message}</p>}
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="category">Kategoriya</Label>
                    <Input id="category" placeholder="Masalan: Xavfsizlik" {...register("category")} />
                    {errors.category && <p className="text-xs text-red-500">{errors.category.message}</p>}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="priority">Ustuvorlik (1-10)</Label>
                    <Input id="priority" type="number" min={1} max={10} {...register("priority", { valueAsNumber: true })} />
                    {errors.priority && <p className="text-xs text-red-500">{errors.priority.message}</p>}
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <input type="checkbox" id="active" className="h-4 w-4 rounded border-gray-300" {...register("active")} />
                  <Label htmlFor="active">Faol</Label>
                </div>

                <DialogFooter>
                  <DialogClose render={<Button variant="outline" />}>Bekor</DialogClose>
                  <Button type="submit" className="bg-blue-600 hover:bg-blue-700" disabled={createMutation.isPending}>
                    {createMutation.isPending ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" />Saqlanmoqda...</> : editingRule ? "Yangilash" : "Qo'shish"}
                  </Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {/* File upload dialog */}
      <Dialog open={uploadDialogOpen} onOpenChange={(open) => {
        setUploadDialogOpen(open);
        if (!open) { setSelectedFile(null); setUploadTitle(""); setUploadCategory(""); }
      }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Fayldan qoida yaratish</DialogTitle>
            <DialogDescription>
              Hujjat yuklanadi, matni o&#39;qiladi va qoida sifatida saqlanadi.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div
              className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-gray-300 p-6 cursor-pointer hover:border-blue-400 transition-colors"
              onClick={() => fileInputRef.current?.click()}
            >
              {selectedFile ? (
                <>
                  <FileText className="h-8 w-8 text-blue-600 mb-2" />
                  <p className="text-sm font-medium">{selectedFile.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {(selectedFile.size / 1024).toFixed(1)} KB
                  </p>
                </>
              ) : (
                <>
                  <Upload className="h-8 w-8 text-gray-400 mb-2" />
                  <p className="text-sm text-muted-foreground">Fayl tanlash uchun bosing</p>
                  <p className="text-xs text-muted-foreground">PDF, DOCX, TXT, DOC</p>
                </>
              )}
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept=".pdf,.docx,.doc,.txt,.md"
              className="hidden"
              onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
            />

            <div className="space-y-2">
              <Label>Sarlavha (ixtiyoriy)</Label>
              <Input
                placeholder="Fayl nomidan foydalaniladi"
                value={uploadTitle}
                onChange={(e) => setUploadTitle(e.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label>Kategoriya (ixtiyoriy)</Label>
              <Input
                placeholder="GENERAL"
                value={uploadCategory}
                onChange={(e) => setUploadCategory(e.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <DialogClose render={<Button variant="outline" />}>Bekor</DialogClose>
            <Button
              className="bg-blue-600 hover:bg-blue-700"
              disabled={!selectedFile || uploadMutation.isPending}
              onClick={() => uploadMutation.mutate()}
            >
              {uploadMutation.isPending ? (
                <><Loader2 className="mr-2 h-4 w-4 animate-spin" />O&#39;qilmoqda...</>
              ) : (
                <><Upload className="mr-2 h-4 w-4" />Yuklash</>
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {!rules || rules.length === 0 ? (
        <EmptyState icon={Brain} title="AI qoidalari yo'q" description="Qoidalar qo'shing.">
          <Button className="bg-blue-600 hover:bg-blue-700" onClick={openCreate}>
            <Plus className="mr-2 h-4 w-4" />
            Qoida qo&#39;shish
          </Button>
        </EmptyState>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {rules.map((rule) => (
            <Card key={rule.id} className={!rule.active ? "opacity-60" : ""}>
              <CardHeader>
                <div className="flex items-start justify-between">
                  <CardTitle className="text-base">{rule.title}</CardTitle>
                  <div className="flex items-center gap-1">
                    <Button variant="ghost" size="icon-xs" onClick={() => toggleMutation.mutate({ id: rule.id, active: !rule.active })} title={rule.active ? "O&apos;chirish" : "Yoqish"}>
                      {rule.active ? <ToggleRight className="h-4 w-4 text-green-600" /> : <ToggleLeft className="h-4 w-4 text-gray-400" />}
                    </Button>
                    <Button variant="ghost" size="icon-xs" onClick={() => openEdit(rule)}>
                      <Pencil className="h-3 w-3" />
                    </Button>
                    <Button variant="ghost" size="icon-xs" onClick={() => { if (confirm("Qoidani o'chirishni tasdiqlaysizmi?")) deleteMutation.mutate(rule.id); }}>
                      <Trash2 className="h-3 w-3 text-red-500" />
                    </Button>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground line-clamp-3 mb-3">{rule.content}</p>
                <div className="flex items-center gap-2">
                  <Badge variant="secondary">{rule.category}</Badge>
                  <Badge variant="outline">Ustuvorlik: {rule.priority}</Badge>
                  {rule.active ? (
                    <Badge variant="secondary" className="bg-green-100 text-green-700">Faol</Badge>
                  ) : (
                    <Badge variant="secondary" className="bg-gray-100 text-gray-700">Nofaol</Badge>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
