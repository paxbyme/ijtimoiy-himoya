import { LucideIcon } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

type CardColor = "blue" | "violet" | "emerald" | "amber";

const colorMap: Record<CardColor, { iconBg: string; iconText: string; accent: string }> = {
  blue:    { iconBg: "bg-blue-50",    iconText: "text-blue-600",    accent: "border-t-2 border-t-blue-500" },
  violet:  { iconBg: "bg-violet-50",  iconText: "text-violet-600",  accent: "border-t-2 border-t-violet-500" },
  emerald: { iconBg: "bg-emerald-50", iconText: "text-emerald-600", accent: "border-t-2 border-t-emerald-500" },
  amber:   { iconBg: "bg-amber-50",   iconText: "text-amber-600",   accent: "border-t-2 border-t-amber-500" },
};

interface StatsCardProps {
  title: string;
  value: string | number;
  icon: LucideIcon;
  description?: string;
  color?: CardColor;
  trend?: {
    value: number;
    isPositive: boolean;
  };
  className?: string;
}

export function StatsCard({ title, value, icon: Icon, description, trend, color = "blue", className }: StatsCardProps) {
  const { iconBg, iconText, accent } = colorMap[color];
  return (
    <Card className={cn("shadow-sm hover:shadow-md transition-shadow duration-200 cursor-default", accent, className)}>
      <CardContent>
        <div className="flex items-center justify-between">
          <div className="space-y-1">
            <p className="text-sm font-medium text-muted-foreground">{title}</p>
            <p className="text-2xl font-bold tracking-tight">{value}</p>
            {description && (
              <p className="text-xs text-muted-foreground">{description}</p>
            )}
            {trend && (
              <p className={cn(
                "text-xs font-medium",
                trend.isPositive ? "text-emerald-600" : "text-red-600"
              )}>
                {trend.isPositive ? "+" : ""}{trend.value}% from last period
              </p>
            )}
          </div>
          <div className={cn("rounded-xl p-3", iconBg)}>
            <Icon className={cn("h-5 w-5", iconText)} />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
