"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "./useAuth";

type Role = "DEVELOPER" | "MANAGER" | "STAFF";

export function useRequireRole(allowed: Role[]) {
  const { userData, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (loading) return;
    if (!userData) {
      router.replace("/login");
      return;
    }
    if (!allowed.includes(userData.role as Role)) {
      router.replace("/dashboard");
    }
  }, [loading, userData, router, allowed]);

  const authorized =
    !loading && !!userData && allowed.includes(userData.role as Role);

  return { userData, loading, authorized };
}
