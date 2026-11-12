import { apiClient } from "@/shared/api";

export const manageAccount = (action: "logout" | "withdraw", token: string): Promise<void> =>
  apiClient<void>(action === "logout" ? "/auth/logout" : "/auth/account", {
    method: action === "logout" ? "POST" : "DELETE",
    token,
    credentials: "include",
    responseType: "empty",
  });
