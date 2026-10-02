import { apiClient } from "@/shared/api";
import type { AnonymousSession } from "../model/types";

export const loginWithLinkCode = (linkCode: string) =>
  apiClient<AnonymousSession>("/auth/link", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ linkCode }),
    errorTracking: {
      level: "fatal",
      operation: "room_entry.login_with_link_code",
      route: "/auth/link",
    },
  });
