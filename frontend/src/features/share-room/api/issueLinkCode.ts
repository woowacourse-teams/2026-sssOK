import { z } from "zod";
export const LinkCodeResultSchema = z.object({ linkCode: z.string(), expiresAt: z.string() });
import { apiClient } from "@/shared/api";

export type LinkCodeResult = z.infer<typeof LinkCodeResultSchema>;

export const issueLinkCode = (accessToken: string): Promise<LinkCodeResult> =>
  apiClient<LinkCodeResult>("/auth/link-code", {
    schema: LinkCodeResultSchema,
    method: "POST",
    token: accessToken,
  });
