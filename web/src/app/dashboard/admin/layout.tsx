"use client";

import { useRequireRole } from "@/hooks/useRequireRole";
import { LoadingSpinner } from "@/components/LoadingSpinner";

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { authorized, loading } = useRequireRole(["DEVELOPER"]);

  if (loading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <LoadingSpinner text="Loading..." />
      </div>
    );
  }

  if (!authorized) return null;

  return <>{children}</>;
}
