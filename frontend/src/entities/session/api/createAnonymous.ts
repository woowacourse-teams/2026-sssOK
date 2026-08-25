import { apiClient } from "@/shared/api";
import type { AnonymousSession, CreateAnonymousRequest } from "../model/types";

export const createAnonymous = (request: CreateAnonymousRequest) => {
  return apiClient<AnonymousSession>("/auth/anonymous", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
};
