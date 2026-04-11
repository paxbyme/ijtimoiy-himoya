"use client";

import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { adminApi } from "@/lib/adminApi";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { ArrowLeft, Phone, Calendar, Loader2, UserX } from "lucide-react";
import Link from "next/link";
import { format } from "date-fns";

const editSchema = z.object({
  displayName: z.string().min(2, "Name must be at least 2 characters"),
});

type EditData = z.infer<typeof editSchema>;

export default function ManagerDetailPage() {
  const params = useParams();
  const router = useRouter();
  const qc = useQueryClient();
  const id = params.id as string;

  const { data: manager, isLoading } = useQuery({
    queryKey: ["admin", "managers", id],
    queryFn: () => adminApi.managers.get(id),
  });

  const { data: deptsData } = useQuery({
    queryKey: ["admin", "departments"],
    queryFn: () => adminApi.departments.list(0, 100),
  });

  const deptName = deptsData?.content.find(
    (d) => d.id === manager?.departmentId
  )?.name;

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<EditData>({
    resolver: zodResolver(editSchema),
    values: { displayName: manager?.name ?? "" },
  });

  const updateMutation = useMutation({
    mutationFn: (data: EditData) =>
      adminApi.managers.update(id, { displayName: data.displayName }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "managers"] });
      toast.success("Manager updated");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to update");
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: () => adminApi.managers.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "managers"] });
      toast.success("Manager deactivated");
      router.push("/dashboard/admin/managers");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Failed to deactivate");
    },
  });

  if (isLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <LoadingSpinner text="Loading manager..." />
      </div>
    );
  }

  if (!manager) {
    return (
      <EmptyState
        title="Manager not found"
        description="The manager you are looking for does not exist."
      />
    );
  }

  const initials = manager.name
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Link href="/dashboard/admin/managers">
          <Button variant="ghost" size="sm">
            <ArrowLeft className="mr-1 h-4 w-4" />
            Back
          </Button>
        </Link>
        <h1 className="text-2xl font-bold tracking-tight">Manager Details</h1>
      </div>

      {/* Profile card */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex items-start gap-4">
            <Avatar size="lg">
              <AvatarFallback className="text-lg">{initials}</AvatarFallback>
            </Avatar>
            <div className="flex-1">
              <div className="flex items-center gap-3">
                <h2 className="text-xl font-bold">{manager.name}</h2>
                <Badge
                  variant="secondary"
                  className={
                    manager.isActive
                      ? "bg-green-100 text-green-700"
                      : "bg-gray-100 text-gray-700"
                  }
                >
                  {manager.isActive ? "ACTIVE" : "INACTIVE"}
                </Badge>
              </div>
              <div className="mt-2 flex flex-wrap gap-4 text-sm text-muted-foreground">
                <div className="flex items-center gap-1">
                  <Phone className="h-4 w-4" />
                  {manager.phone}
                </div>
                {manager.createdAt && (
                  <div className="flex items-center gap-1">
                    <Calendar className="h-4 w-4" />
                    Joined {format(new Date(manager.createdAt), "MMMM d, yyyy")}
                  </div>
                )}
                {deptName && (
                  <div className="text-blue-600 font-medium">
                    Department: {deptName}
                  </div>
                )}
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Edit form */}
      <Card>
        <CardHeader>
          <CardTitle>Edit Manager</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={handleSubmit((data) => updateMutation.mutate(data))}
            className="space-y-4 max-w-sm"
          >
            <div className="space-y-2">
              <Label htmlFor="displayName">Full Name</Label>
              <Input
                id="displayName"
                placeholder="Enter full name"
                {...register("displayName")}
              />
              {errors.displayName && (
                <p className="text-xs text-red-500">{errors.displayName.message}</p>
              )}
            </div>
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
          </form>
        </CardContent>
      </Card>

      {/* Deactivate */}
      {manager.isActive && (
        <Card className="border-red-200">
          <CardHeader>
            <CardTitle className="text-red-600">Danger Zone</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground mb-4">
              Deactivating this manager will disable their account. They will no longer
              be able to sign in.
            </p>
            <Button
              variant="outline"
              className="border-red-300 text-red-600 hover:bg-red-50"
              disabled={deactivateMutation.isPending}
              onClick={() => deactivateMutation.mutate()}
            >
              {deactivateMutation.isPending ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Deactivating...
                </>
              ) : (
                <>
                  <UserX className="mr-2 h-4 w-4" />
                  Deactivate Manager
                </>
              )}
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
