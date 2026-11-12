import { AnonymousSessionSchema } from "../model/types";
import { apiClient } from "@/shared/api";
import type { AnonymousSession, CreateAnonymousRequest } from "../model/types";

export const createAnonymous = (request: CreateAnonymousRequest): Promise<AnonymousSession> => {
  return apiClient<AnonymousSession>("/auth/anonymous", {
    schema: AnonymousSessionSchema,
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-Protection": "1",
    },
    credentials: "include",
    body: JSON.stringify({ ...request, refreshTokenDelivery: "COOKIE" }),
    errorTracking: {
      level: "fatal",
      operation: "room_entry.create_anonymous_session",
      route: "/auth/anonymous",
    },
  });
};
