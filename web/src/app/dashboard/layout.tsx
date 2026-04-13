"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";
import { Sidebar } from "@/components/Sidebar";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { LogOut, Settings, User } from "lucide-react";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, userData, loading, logout } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) {
      router.replace("/login");
    }
  }, [user, loading, router]);

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center">
        <LoadingSpinner size="lg" text="Loading dashboard..." />
      </div>
    );
  }

  if (!user) {
    return null;
  }

  const handleLogout = async () => {
    await logout();
    router.replace("/login");
  };

  const initials = userData?.name
    ? userData.name
        .split(" ")
        .map((n) => n[0])
        .join("")
        .toUpperCase()
        .slice(0, 2)
    : "U";

  return (
    <div className="flex h-screen overflow-hidden bg-slate-50">
      <Sidebar />

      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Top Header */}
        <header className="flex h-14 items-center justify-between border-b bg-white shadow-sm px-4 lg:px-6">
          <div className="lg:hidden" />
          <div className="hidden lg:block">
            <h2 className="text-sm font-medium text-muted-foreground">
              Xush kelibsiz, {userData?.name || "Foydalanuvchi"}
            </h2>
          </div>

          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button variant="ghost" className="gap-2 px-2">
                  <Avatar size="sm">
                    <AvatarFallback>{initials}</AvatarFallback>
                  </Avatar>
                  <span className="hidden text-sm font-medium sm:inline-block">
                    {userData?.name || "Foydalanuvchi"}
                  </span>
                </Button>
              }
            />
            <DropdownMenuContent align="end" sideOffset={8}>
              <DropdownMenuItem>
                <User className="mr-2 h-4 w-4" />
                Profil
              </DropdownMenuItem>
              <DropdownMenuItem>
                <Settings className="mr-2 h-4 w-4" />
                Sozlamalar
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={handleLogout}>
                <LogOut className="mr-2 h-4 w-4" />
                Chiqish
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </header>

        {/* Main Content with watermark */}
        <main className="relative flex-1 overflow-y-auto bg-slate-50 p-4 lg:p-6">
          {/* SJMA watermark */}
          <div
            aria-hidden="true"
            className="pointer-events-none fixed inset-0 z-0 flex items-center justify-center"
            style={{ top: 56 }}
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="/logo.png"
              alt=""
              className="h-[420px] w-[420px] select-none opacity-[0.04]"
              draggable={false}
            />
          </div>
          <div className="relative z-10">{children}</div>
        </main>
      </div>
    </div>
  );
}
