"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Users,
  CheckSquare,
  Brain,
  BarChart3,
  MessageSquare,
  FileText,
  ChevronLeft,
  Menu,
  Bot,
  UserCog,
  Building2,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import { useState } from "react";
import { useAuth } from "@/hooks/useAuth";

type Role = "DEVELOPER" | "MANAGER" | "STAFF";

const navItems: Array<{
  href: string;
  label: string;
  icon: React.ElementType;
  roles: Role[];
}> = [
  { href: "/dashboard",                    label: "Dashboard",   icon: LayoutDashboard, roles: ["MANAGER", "DEVELOPER"] },
  { href: "/dashboard/employees",          label: "Employees",   icon: Users,           roles: ["MANAGER"] },
  { href: "/dashboard/tasks",              label: "Tasks",       icon: CheckSquare,     roles: ["MANAGER"] },
  { href: "/dashboard/ai-chat",            label: "AI Chat",     icon: Bot,             roles: ["MANAGER"] },
  { href: "/dashboard/ai-rules",           label: "AI Rules",    icon: Brain,           roles: ["MANAGER"] },
  { href: "/dashboard/kpi",               label: "KPI",         icon: BarChart3,        roles: ["MANAGER"] },
  { href: "/dashboard/chat",               label: "Chat",        icon: MessageSquare,   roles: ["MANAGER"] },
  { href: "/dashboard/documents",          label: "Documents",   icon: FileText,        roles: ["MANAGER"] },
  { href: "/dashboard/admin/managers",     label: "Managers",    icon: UserCog,         roles: ["DEVELOPER"] },
  { href: "/dashboard/admin/departments",  label: "Departments", icon: Building2,       roles: ["DEVELOPER"] },
];

function NavContent({ collapsed = false, onItemClick }: { collapsed?: boolean; onItemClick?: () => void }) {
  const pathname = usePathname();
  const { userData } = useAuth();
  const role = (userData?.role ?? "MANAGER") as Role;
  const visibleItems = navItems.filter((item) => item.roles.includes(role));

  return (
    <nav className="flex flex-col gap-1 px-3 py-2">
      {visibleItems.map((item) => {
        const isActive =
          item.href === "/dashboard"
            ? pathname === "/dashboard"
            : pathname.startsWith(item.href);

        return (
          <Link
            key={item.href}
            href={item.href}
            onClick={onItemClick}
            className={cn(
              "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all duration-200",
              isActive
                ? "bg-white/15 text-white shadow-sm"
                : "text-blue-200 hover:bg-white/10 hover:text-white"
            )}
          >
            <item.icon className="h-4 w-4 shrink-0" />
            {!collapsed && <span>{item.label}</span>}
          </Link>
        );
      })}
    </nav>
  );
}

export function Sidebar() {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <>
      {/* Desktop Sidebar */}
      <aside
        className={cn(
          "hidden lg:flex flex-col transition-all duration-300 bg-gradient-to-b from-blue-900 to-blue-700",
          collapsed ? "w-16" : "w-64"
        )}
      >
        <div className="flex h-14 items-center justify-between px-4 border-b border-white/10">
          {!collapsed && (
            <h1 className="text-lg font-bold text-white tracking-tight">Manager</h1>
          )}
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => setCollapsed(!collapsed)}
            className="text-white/70 hover:text-white hover:bg-white/10"
          >
            <ChevronLeft
              className={cn(
                "h-4 w-4 transition-transform",
                collapsed && "rotate-180"
              )}
            />
          </Button>
        </div>
        <ScrollArea className="flex-1">
          <NavContent collapsed={collapsed} />
        </ScrollArea>
      </aside>

      {/* Mobile Sidebar */}
      <Sheet>
        <SheetTrigger
          className="lg:hidden fixed top-3 left-3 z-40"
          render={<Button variant="outline" size="icon" />}
        >
          <Menu className="h-4 w-4" />
        </SheetTrigger>
        <SheetContent side="left" className="w-64 p-0 bg-gradient-to-b from-blue-900 to-blue-700 border-r-0">
          <div className="flex h-14 items-center px-4 border-b border-white/10">
            <h1 className="text-lg font-bold text-white tracking-tight">Manager</h1>
          </div>
          <ScrollArea className="flex-1">
            <NavContent />
          </ScrollArea>
        </SheetContent>
      </Sheet>
    </>
  );
}
