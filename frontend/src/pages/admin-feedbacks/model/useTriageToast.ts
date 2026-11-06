import { useCallback } from "react";

import { useToast } from "@/shared/ui/toast";

import { isApiError } from "@/shared/api";

export const useTriageToast = (onUnauthorized: () => void) => {
  const { showToast } = useToast();

  const showChanged = useCallback(
    (message: string) => showToast({ tone: "success", message, showClose: false }),
    [showToast],
  );

  const showFailed = useCallback(
    (target: "상태" | "담당자") => (error: unknown) => {
      if (isApiError(error) && error.status === 401) {
        onUnauthorized();
        return;
      }

      showToast({
        showClose: false,
        tone: "error",
        message: `${target === "상태" ? "상태를" : "담당자를"} 바꾸지 못했어요. 다시 시도해 주세요.`,
      });
    },
    [onUnauthorized, showToast],
  );

  return { showChanged, showFailed };
};
