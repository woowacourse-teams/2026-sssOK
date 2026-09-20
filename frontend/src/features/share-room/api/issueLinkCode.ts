import { apiClient } from "@/shared/api";

export interface LinkCodeResult {
  linkCode: string;
  expiresAt: string;
}

export const issueLinkCode = (accessToken: string) =>
  apiClient<LinkCodeResult>("/auth/link-code", {
    method: "POST",
    token: accessToken,
  });
