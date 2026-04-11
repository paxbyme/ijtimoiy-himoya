"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { adminApi } from "@/lib/adminApi";
import { Department } from "@/types";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Building2, Plus, Pencil, Trash2, Loader2 } from "lucide-react";

const deptSchema = z.object({
  name: z.string().min(2, "Name must be at least 2 characters"),
  managerId: z.string().optional(),
});

type DeptFormData = z.infer<typeof deptSchema>;

export default function DepartmentsPage() {
  const [createOpen, setCreateOpen] = useState(false);
  const [editDept, setEditDept] = useState<Department | null>(null);
  const qc = useQueryClient();

  const { data: deptsData, isLoading } = useQuery({
    queryKey: ["admin", "departments"],
    queryFn: () => adminApi.departments.list(0, 100),
  });

  const { data: managersData } = useQuery({
    queryKey: ["admin", "managers"],
    queryFn: () => adminApi.managers.list(0, 100),
  });

  const departments = deptsData?.content ?? [];
  const managers = managersData?.content ?? [];
  const managerMap = Object.fromEntries(managers.map((m) => [m.id, m.name]));

  // ── Create form ──
  const createForm = useForm<DeptFormData>({
    resolver: zodResolver(deptSchema),
    defaultValues: { name: "", managerId: "" },
  });

  // ── Edit form ──
  const editForm = useForm<DeptFormData>({
    resolver: zodResolver(deptSchema),
    values: { name: editDept?.name ?? "", managerId: editDept?.managerId ?? "" },
  });

  const createMutation = useMutation({
    mutationFn: (data: DeptFormData) =>
      adminApi.departments.create({
        name: data.name,
        managerId: data.managerId || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "departments"] });
      qc.invalidateQueries({ queryKey: ["admin", "managers"] });
      toast.success("Department created");
      setCreateOpen(false);
      createForm.reset();
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to create department");
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: DeptFormData }) =>
      adminApi.departments.update(id, {
        name: data.name,
        managerId: data.managerId || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "departments"] });
      qc.invalidateQueries({ queryKey: ["admin", "managers"] });
      toast.success("Department updated");
      setEditDept(null);
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to update department");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminApi.departments.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "departments"] });
      toast.success("Department deleted");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to delete department");
    },
  });

  if (isLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <LoadingSpinner text="Loading departments..." />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Departments</h1>
          <p className="text-sm text-muted-foreground">
            Manage departments and assign managers
          </p>
        </div>

        {/* Create dialog */}
        <Dialog open={createOpen} onOpenChange={setCreateOpen}>
          <DialogTrigger render={<Button className="bg-blue-600 hover:bg-blue-700" />}>
            <Plus className="mr-2 h-4 w-4" />
            Add Department
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Add New Department</DialogTitle>
              <DialogDescription>
                Create a department and optionally assign a manager.
              </DialogDescription>
            </DialogHeader>
            <form
              onSubmit={createForm.handleSubmit((data) =>
                createMutation.mutate(data)
              )}
              className="space-y-4"
            >
              <div className="space-y-2">
                <Label htmlFor="name">Department Name</Label>
                <Input
                  id="name"
                  placeholder="e.g. Engineering"
                  {...createForm.register("name")}
                />
                {createForm.formState.errors.name && (
                  <p className="text-xs text-red-500">
                    {createForm.formState.errors.name.message}
                  </p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="managerId">
                  Manager <span className="text-muted-foreground">(optional)</span>
                </Label>
                <select
                  id="managerId"
                  {...createForm.register("managerId")}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                >
                  <option value="">— Assign later —</option>
                  {managers
                    .filter((m) => m.isActive)
                    .map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.name}
                      </option>
                    ))}
                </select>
              </div>
              <DialogFooter>
                <DialogClose render={<Button variant="outline" type="button" />}>
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
                      Creating...
                    </>
                  ) : (
                    "Create Department"
                  )}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      {/* Edit dialog */}
      <Dialog open={!!editDept} onOpenChange={(o) => !o && setEditDept(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Edit Department</DialogTitle>
          </DialogHeader>
          <form
            onSubmit={editForm.handleSubmit((data) =>
              updateMutation.mutate({ id: editDept!.id, data })
            )}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label htmlFor="edit-name">Department Name</Label>
              <Input
                id="edit-name"
                placeholder="Department name"
                {...editForm.register("name")}
              />
              {editForm.formState.errors.name && (
                <p className="text-xs text-red-500">
                  {editForm.formState.errors.name.message}
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="edit-managerId">Manager</Label>
              <select
                id="edit-managerId"
                {...editForm.register("managerId")}
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              >
                <option value="">— None —</option>
                {managers
                  .filter((m) => m.isActive)
                  .map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name}
                    </option>
                  ))}
              </select>
            </div>
            <DialogFooter>
              <DialogClose render={<Button variant="outline" type="button" />}>
                Cancel
              </DialogClose>
              <Button
                type="submit"
                className="bg-blue-600 hover:bg-blue-700"
                disabled={updateMutation.isPending}
              >
                {updateMutation.isPending ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Saving...
                  </>
                ) : (
                  "Save Changes"
                )}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {departments.length === 0 ? (
        <EmptyState
          icon={Building2}
          title="No departments yet"
          description="Create your first department to get started."
        >
          <Button
            className="bg-blue-600 hover:bg-blue-700"
            onClick={() => setCreateOpen(true)}
          >
            <Plus className="mr-2 h-4 w-4" />
            Add Department
          </Button>
        </EmptyState>
      ) : (
        <div className="rounded-lg border bg-white">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Department</TableHead>
                <TableHead>Manager</TableHead>
                <TableHead>Created</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {departments.map((dept: Department) => (
                <TableRow key={dept.id}>
                  <TableCell className="font-medium">{dept.name}</TableCell>
                  <TableCell>
                    {dept.managerId && managerMap[dept.managerId] ? (
                      managerMap[dept.managerId]
                    ) : (
                      <span className="text-muted-foreground">Unassigned</span>
                    )}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {dept.createdAt
                      ? new Date(dept.createdAt).toLocaleDateString()
                      : "—"}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setEditDept(dept)}
                      >
                        <Pencil className="mr-1 h-4 w-4" />
                        Edit
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-red-600 hover:text-red-700 hover:bg-red-50"
                        disabled={deleteMutation.isPending}
                        onClick={() => deleteMutation.mutate(dept.id)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
