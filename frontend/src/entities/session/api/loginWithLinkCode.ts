import { apiClient } from "@/shared/api";
import type { AnonymousSession } from "../model/types";

export const loginWithLinkCode = (linkCode: string) =>
  apiClient<AnonymousSession>("/auth/link", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-CSRF-Protection": "1" },
    credentials: "include",
    body: JSON.stringify({ linkCode, refreshTokenDelivery: "COOKIE" }),
    errorTracking: {
      level: "fatal",
      operation: "room_entry.login_with_link_code",
      route: "/auth/link",
    },
  });
