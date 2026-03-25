"use client";

import { useState } from "react";
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
import { Plus, Brain, Pencil, Trash2, Loader2, ToggleLeft, ToggleRight } from "lucide-react";

const ruleSchema = z.object({
  title: z.string().min(2, "Title is required"),
  content: z.string().min(1, "Content is required"),
  category: z.string().min(1, "Category is required"),
  priority: z.number().min(1).max(10),
  active: z.boolean(),
});

type RuleFormData = z.infer<typeof ruleSchema>;

export default function AiRulesPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<AiRule | null>(null);
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
    defaultValues: {
      priority: 5,
      active: true,
    },
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ai-rules"] });
      toast.success(editingRule ? "Rule updated successfully" : "Rule created successfully");
      setDialogOpen(false);
      setEditingRule(null);
      reset();
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to save rule");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/ai-rules/${id}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ai-rules"] });
      toast.success("Rule deleted successfully");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to delete rule");
    },
  });

  const toggleMutation = useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => {
      const res = await api.put(`/ai-rules/${id}`, { isActive: active });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ai-rules"] });
      toast.success("Rule status updated");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to toggle rule");
    },
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
    reset({
      title: "",
      content: "",
      category: "",
      priority: 5,
      active: true,
    });
    setDialogOpen(true);
  };

  const onSubmit = (data: RuleFormData) => {
    createMutation.mutate(data);
  };

  if (isLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <LoadingSpinner text="Loading AI rules..." />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">AI Rules</h1>
          <p className="text-sm text-muted-foreground">
            Configure rules that guide AI behavior for your team
          </p>
        </div>
        <Dialog open={dialogOpen} onOpenChange={(open) => {
          setDialogOpen(open);
          if (!open) {
            setEditingRule(null);
            reset();
          }
        }}>
          <DialogTrigger render={<Button className="bg-blue-600 hover:bg-blue-700" onClick={openCreate} />}>
            <Plus className="mr-2 h-4 w-4" />
            Add Rule
          </DialogTrigger>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>
                {editingRule ? "Edit Rule" : "Add New Rule"}
              </DialogTitle>
              <DialogDescription>
                {editingRule
                  ? "Update the AI rule configuration."
                  : "Define a new rule for the AI assistant."}
              </DialogDescription>
            </DialogHeader>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="title">Title</Label>
                <Input
                  id="title"
                  placeholder="Rule title"
                  {...register("title")}
                />
                {errors.title && (
                  <p className="text-xs text-red-500">{errors.title.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="content">Content</Label>
                <Textarea
                  id="content"
                  placeholder="Describe the rule..."
                  {...register("content")}
                />
                {errors.content && (
                  <p className="text-xs text-red-500">{errors.content.message}</p>
                )}
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="category">Category</Label>
                  <Input
                    id="category"
                    placeholder="e.g., Safety, Conduct"
                    {...register("category")}
                  />
                  {errors.category && (
                    <p className="text-xs text-red-500">{errors.category.message}</p>
                  )}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="priority">Priority (1-10)</Label>
                  <Input
                    id="priority"
                    type="number"
                    min={1}
                    max={10}
                    {...register("priority", { valueAsNumber: true })}
                  />
                  {errors.priority && (
                    <p className="text-xs text-red-500">{errors.priority.message}</p>
                  )}
                </div>
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="active"
                  className="h-4 w-4 rounded border-gray-300"
                  {...register("active")}
                />
                <Label htmlFor="active">Active</Label>
              </div>

              <DialogFooter>
                <DialogClose render={<Button variant="outline" />}>
                  Cancel
                </DialogClose>
                <Button
                  type="submit"
                  className="bg-blue-600 hover:bg-blue-700"
                  disabled={createMutation.isPending}
                >
                  {createMutation.isPending ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Saving...
                    </>
                  ) : editingRule ? (
                    "Update Rule"
                  ) : (
                    "Add Rule"
                  )}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      {!rules || rules.length === 0 ? (
        <EmptyState
          icon={Brain}
          title="No AI rules defined"
          description="Add rules to configure how the AI assistant interacts with your team."
        >
          <Button className="bg-blue-600 hover:bg-blue-700" onClick={openCreate}>
            <Plus className="mr-2 h-4 w-4" />
            Add Rule
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
                    <Button
                      variant="ghost"
                      size="icon-xs"
                      onClick={() =>
                        toggleMutation.mutate({
                          id: rule.id,
                          active: !rule.active,
                        })
                      }
                      title={rule.active ? "Deactivate" : "Activate"}
                    >
                      {rule.active ? (
                        <ToggleRight className="h-4 w-4 text-green-600" />
                      ) : (
                        <ToggleLeft className="h-4 w-4 text-gray-400" />
                      )}
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-xs"
                      onClick={() => openEdit(rule)}
                    >
                      <Pencil className="h-3 w-3" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-xs"
                      onClick={() => {
                        if (confirm("Are you sure you want to delete this rule?")) {
                          deleteMutation.mutate(rule.id);
                        }
                      }}
                    >
                      <Trash2 className="h-3 w-3 text-red-500" />
                    </Button>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground line-clamp-3 mb-3">
                  {rule.content}
                </p>
                <div className="flex items-center gap-2">
                  <Badge variant="secondary">{rule.category}</Badge>
                  <Badge variant="outline">Priority: {rule.priority}</Badge>
                  {rule.active ? (
                    <Badge variant="secondary" className="bg-green-100 text-green-700">
                      Active
                    </Badge>
                  ) : (
                    <Badge variant="secondary" className="bg-gray-100 text-gray-700">
                      Inactive
                    </Badge>
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
