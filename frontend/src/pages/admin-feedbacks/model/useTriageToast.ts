import { useCallback, useEffect, useState } from "react";

import { isApiError } from "@/shared/api";

export const TRIAGE_TOAST_MS = 2500;

interface TriageToast {
  id: number;
  tone: "success" | "error";
  message: string;
}

export const useTriageToast = (onUnauthorized: () => void) => {
  const [toast, setToast] = useState<TriageToast | null>(null);

  useEffect(() => {
    if (!toast) return;

    const timer = window.setTimeout(() => setToast(null), TRIAGE_TOAST_MS);

    return () => window.clearTimeout(timer);
  }, [toast]);

  const showChanged = useCallback(
    (message: string) => setToast({ id: Date.now(), tone: "success", message }),
    [],
  );

  const showFailed = useCallback(
    (target: "상태" | "담당자") => (error: unknown) => {
      if (isApiError(error) && error.status === 401) {
        onUnauthorized();
        return;
      }

      setToast({
        id: Date.now(),
        tone: "error",
        message: `${target === "상태" ? "상태를" : "담당자를"} 바꾸지 못했어요. 다시 시도해 주세요.`,
      });
    },
    [onUnauthorized],
  );

  return { toast, showChanged, showFailed, closeToast: () => setToast(null) };
};
