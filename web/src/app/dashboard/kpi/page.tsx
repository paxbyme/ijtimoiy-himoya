"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import api from "@/lib/api";
import { KpiScore } from "@/types";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { EmptyState } from "@/components/EmptyState";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
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
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { BarChart3, Trophy, Medal } from "lucide-react";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from "recharts";

const barColors = ["#2563EB", "#3B82F6", "#60A5FA", "#93C5FD", "#BFDBFE"];

export default function KpiPage() {
  const [period, setPeriod] = useState("current");

  const { data: scores, isLoading } = useQuery({
    queryKey: ["kpi-scores", period],
    queryFn: async () => {
      const res = await api.get(`/kpi/rankings`, {
        params: { period: period === "current" ? undefined : period },
      });
      return (res.data.data || res.data) as KpiScore[];
    },
  });

  if (isLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <LoadingSpinner text="Loading KPI data..." />
      </div>
    );
  }

  const sortedScores = [...(scores || [])].sort(
    (a, b) => b.score - a.score
  );

  const chartData = sortedScores.map((score) => ({
    name: score.userName || `User ${score.userId.slice(0, 6)}`,
    score: score.score,
  }));

  const getRankBadge = (index: number) => {
    if (index === 0)
      return (
        <Badge variant="secondary" className="bg-yellow-100 text-yellow-700">
          <Trophy className="mr-1 h-3 w-3" />
          1st
        </Badge>
      );
    if (index === 1)
      return (
        <Badge variant="secondary" className="bg-gray-100 text-gray-700">
          <Medal className="mr-1 h-3 w-3" />
          2nd
        </Badge>
      );
    if (index === 2)
      return (
        <Badge variant="secondary" className="bg-orange-100 text-orange-700">
          <Medal className="mr-1 h-3 w-3" />
          3rd
        </Badge>
      );
    return (
      <Badge variant="outline">
        #{index + 1}
      </Badge>
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">KPI Rankings</h1>
          <p className="text-sm text-muted-foreground">
            Employee performance scores and rankings
          </p>
        </div>
        <Select value={period} onValueChange={(v: string | null) => { if (v) setPeriod(v); }}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="Select period" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="current">Current Period</SelectItem>
            <SelectItem value="2026-Q1">Q1 2026</SelectItem>
            <SelectItem value="2025-Q4">Q4 2025</SelectItem>
            <SelectItem value="2025-Q3">Q3 2025</SelectItem>
            <SelectItem value="2025-Q2">Q2 2025</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {sortedScores.length === 0 ? (
        <EmptyState
          icon={BarChart3}
          title="No KPI data available"
          description="Performance scores will appear here once they are recorded."
        />
      ) : (
        <>
          {/* Bar Chart */}
          <Card>
            <CardHeader>
              <CardTitle>Performance Overview</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="h-[300px]">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis
                      dataKey="name"
                      fontSize={12}
                      angle={-20}
                      textAnchor="end"
                      height={60}
                    />
                    <YAxis domain={[0, 100]} fontSize={12} />
                    <Tooltip />
                    <Bar dataKey="score" radius={[4, 4, 0, 0]}>
                      {chartData.map((_, index) => (
                        <Cell
                          key={`cell-${index}`}
                          fill={barColors[index % barColors.length]}
                        />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>

          {/* Rankings Table */}
          <Card>
            <CardHeader>
              <CardTitle>Rankings</CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Rank</TableHead>
                    <TableHead>Employee</TableHead>
                    <TableHead>Score</TableHead>
                    <TableHead>Period</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sortedScores.map((score, index) => {
                    const name = score.userName || `User ${score.userId.slice(0, 6)}`;
                    const initials = name
                      .split(" ")
                      .map((n) => n[0])
                      .join("")
                      .toUpperCase()
                      .slice(0, 2);

                    return (
                      <TableRow key={score.id}>
                        <TableCell>{getRankBadge(index)}</TableCell>
                        <TableCell>
                          <div className="flex items-center gap-3">
                            <Avatar size="sm">
                              <AvatarFallback>{initials}</AvatarFallback>
                            </Avatar>
                            <span className="font-medium">{name}</span>
                          </div>
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            <div className="h-2 w-20 rounded-full bg-gray-100">
                              <div
                                className="h-full rounded-full bg-blue-600"
                                style={{ width: `${score.score}%` }}
                              />
                            </div>
                            <span className="font-medium">
                              {score.score.toFixed(1)}
                            </span>
                          </div>
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {score.period}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
