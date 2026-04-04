"use client";

import { useQuery } from "@tanstack/react-query";
import api from "@/lib/api";
import { Task, User } from "@/types";
import { StatsCard } from "@/components/StatsCard";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Users, CheckSquare, BarChart3, Clock } from "lucide-react";
import { format } from "date-fns";

const priorityColors: Record<string, string> = {
  LOW: "bg-gray-100 text-gray-700",
  MEDIUM: "bg-blue-100 text-blue-700",
  HIGH: "bg-orange-100 text-orange-700",
  URGENT: "bg-red-100 text-red-700",
};

const statusColors: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-700",
  IN_PROGRESS: "bg-blue-100 text-blue-700",
  COMPLETED: "bg-green-100 text-green-700",
  CANCELLED: "bg-gray-100 text-gray-700",
};

export default function DashboardPage() {
  const { data: staffData, isLoading: staffLoading } = useQuery({
    queryKey: ["staff"],
    queryFn: async () => {
      const res = await api.get("/users/staff");
      const data = res.data.data || res.data;
      return (Array.isArray(data) ? data : data?.content ?? []) as User[];
    },
  });

  const { data: tasksData, isLoading: tasksLoading } = useQuery({
    queryKey: ["tasks"],
    queryFn: async () => {
      const res = await api.get("/tasks");
      const data = res.data.data || res.data;
      return (Array.isArray(data) ? data : data?.content ?? []) as Task[];
    },
  });

  const { data: kpiData } = useQuery({
    queryKey: ["kpi-rankings"],
    queryFn: async () => {
      const res = await api.get("/kpi/rankings");
      const scores = (res.data.data || res.data) as { score: number }[];
      if (!Array.isArray(scores) || scores.length === 0) return 0;
      return scores.reduce((sum, s) => sum + s.score, 0) / scores.length;
    },
  });

  if (staffLoading || tasksLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <LoadingSpinner text="Loading dashboard..." />
      </div>
    );
  }

  const staff = staffData || [];
  const tasks = tasksData || [];
  const averageKpi = typeof kpiData === "number" ? kpiData : 0;
  const pendingTasks = tasks.filter((t) => t.status === "PENDING").length;
  const activeTasks = tasks.filter(
    (t) => t.status === "PENDING" || t.status === "IN_PROGRESS"
  ).length;
  const recentTasks = [...tasks]
    .sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    )
    .slice(0, 5);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Overview of your team and activities
        </p>
      </div>

      {/* Stats */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatsCard
          title="Total Employees"
          value={staff.length}
          icon={Users}
          description={`${staff.filter((s) => s.isActive).length} active`}
        />
        <StatsCard
          title="Active Tasks"
          value={activeTasks}
          icon={CheckSquare}
          description={`${tasks.length} total tasks`}
        />
        <StatsCard
          title="Average KPI"
          value={averageKpi.toFixed(1)}
          icon={BarChart3}
          description="Team performance score"
        />
        <StatsCard
          title="Pending Tasks"
          value={pendingTasks}
          icon={Clock}
          description="Awaiting action"
        />
      </div>

      {/* Recent Tasks */}
      <Card>
        <CardHeader>
          <CardTitle>Recent Tasks</CardTitle>
        </CardHeader>
        <CardContent>
          {recentTasks.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">
              No tasks yet
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Title</TableHead>
                  <TableHead>Assignee</TableHead>
                  <TableHead>Priority</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {recentTasks.map((task) => (
                  <TableRow key={task.id}>
                    <TableCell className="font-medium">{task.title}</TableCell>
                    <TableCell>{task.assigneeName || "Unassigned"}</TableCell>
                    <TableCell>
                      <Badge
                        variant="secondary"
                        className={priorityColors[task.priority]}
                      >
                        {task.priority}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant="secondary"
                        className={statusColors[task.status]}
                      >
                        {task.status.replace("_", " ")}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {format(new Date(task.createdAt), "MMM d, yyyy")}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
