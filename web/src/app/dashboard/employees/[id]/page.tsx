"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import api from "@/lib/api";
import { User, Task, KpiScore } from "@/types";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { EmptyState } from "@/components/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Phone, Calendar, BarChart3, CheckSquare } from "lucide-react";
import { format } from "date-fns";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";

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

export default function EmployeeDetailPage() {
  const params = useParams();
  const employeeId = params.id as string;

  const { data: employee, isLoading: employeeLoading } = useQuery({
    queryKey: ["employee", employeeId],
    queryFn: async () => {
      const res = await api.get(`/users/${employeeId}`);
      return (res.data.data || res.data) as User;
    },
  });

  const { data: tasks, isLoading: tasksLoading } = useQuery({
    queryKey: ["employee-tasks", employeeId],
    queryFn: async () => {
      const res = await api.get(`/tasks?assigneeId=${employeeId}`);
      return (res.data.data || res.data) as Task[];
    },
  });

  const { data: kpiScores, isLoading: kpiLoading } = useQuery({
    queryKey: ["employee-kpi", employeeId],
    queryFn: async () => {
      const res = await api.get(`/kpi/user/${employeeId}`);
      return (res.data.data || res.data) as KpiScore[];
    },
  });

  if (employeeLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <LoadingSpinner text="Loading employee details..." />
      </div>
    );
  }

  if (!employee) {
    return (
      <EmptyState
        title="Employee not found"
        description="The employee you are looking for does not exist."
      />
    );
  }

  const initials = employee.name
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);

  const kpiChartData = (kpiScores || []).map((score) => ({
    period: score.period,
    score: score.score,
  }));

  return (
    <div className="space-y-6">
      {/* Employee Header */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex items-start gap-4">
            <Avatar size="lg">
              <AvatarFallback className="text-lg">{initials}</AvatarFallback>
            </Avatar>
            <div className="flex-1">
              <div className="flex items-center gap-3">
                <h1 className="text-2xl font-bold">{employee.name}</h1>
                <Badge
                  variant="secondary"
                  className={
                    employee.isActive
                      ? "bg-green-100 text-green-700"
                      : "bg-gray-100 text-gray-700"
                  }
                >
                  {employee.isActive ? "ACTIVE" : "INACTIVE"}
                </Badge>
              </div>
              <div className="mt-2 flex flex-wrap gap-4 text-sm text-muted-foreground">
                <div className="flex items-center gap-1">
                  <Phone className="h-4 w-4" />
                  {employee.phone}
                </div>
                <div className="flex items-center gap-1">
                  <Calendar className="h-4 w-4" />
                  Joined {format(new Date(employee.createdAt), "MMMM d, yyyy")}
                </div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Tabs */}
      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="tasks">Tasks</TabsTrigger>
          <TabsTrigger value="kpi">KPI History</TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <div className="grid gap-4 sm:grid-cols-3">
            <Card>
              <CardContent className="pt-4">
                <div className="flex items-center gap-2">
                  <CheckSquare className="h-5 w-5 text-blue-600" />
                  <div>
                    <p className="text-sm text-muted-foreground">Total Tasks</p>
                    <p className="text-xl font-bold">{tasks?.length || 0}</p>
                  </div>
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-4">
                <div className="flex items-center gap-2">
                  <CheckSquare className="h-5 w-5 text-green-600" />
                  <div>
                    <p className="text-sm text-muted-foreground">Completed</p>
                    <p className="text-xl font-bold">
                      {tasks?.filter((t) => t.status === "COMPLETED").length || 0}
                    </p>
                  </div>
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-4">
                <div className="flex items-center gap-2">
                  <BarChart3 className="h-5 w-5 text-purple-600" />
                  <div>
                    <p className="text-sm text-muted-foreground">Latest KPI</p>
                    <p className="text-xl font-bold">
                      {kpiScores && kpiScores.length > 0
                        ? kpiScores[kpiScores.length - 1].score.toFixed(1)
                        : "N/A"}
                    </p>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="tasks">
          {tasksLoading ? (
            <LoadingSpinner text="Loading tasks..." />
          ) : !tasks || tasks.length === 0 ? (
            <EmptyState
              icon={CheckSquare}
              title="No tasks assigned"
              description="This employee has no tasks yet."
            />
          ) : (
            <div className="rounded-lg border bg-white">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Title</TableHead>
                    <TableHead>Priority</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Due Date</TableHead>
                    <TableHead>Created</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {tasks.map((task) => (
                    <TableRow key={task.id}>
                      <TableCell className="font-medium">
                        {task.title}
                      </TableCell>
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
                        {task.dueDate
                          ? format(new Date(task.dueDate), "MMM d, yyyy")
                          : "-"}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {format(new Date(task.createdAt), "MMM d, yyyy")}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </TabsContent>

        <TabsContent value="kpi">
          {kpiLoading ? (
            <LoadingSpinner text="Loading KPI data..." />
          ) : !kpiScores || kpiScores.length === 0 ? (
            <EmptyState
              icon={BarChart3}
              title="No KPI data"
              description="No performance scores recorded yet."
            />
          ) : (
            <Card>
              <CardHeader>
                <CardTitle>KPI Score History</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="h-[300px]">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={kpiChartData}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="period" fontSize={12} />
                      <YAxis domain={[0, 100]} fontSize={12} />
                      <Tooltip />
                      <Line
                        type="monotone"
                        dataKey="score"
                        stroke="#2563EB"
                        strokeWidth={2}
                        dot={{ fill: "#2563EB", r: 4 }}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </CardContent>
            </Card>
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
}
