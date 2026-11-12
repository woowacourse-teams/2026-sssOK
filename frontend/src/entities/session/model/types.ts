import { z } from "zod";
export const AnonymousSessionSchema = z.object({
  accessToken: z.string().min(1),
  userId: z.number(),
  nickname: z.string(),
  expiresAt: z.string().refine((value) => Number.isFinite(Date.parse(value))),
  refreshExpiresAt: z.string().optional(),
});
export interface CreateAnonymousRequest {
  nickname: string;
}

export type AnonymousSession = z.infer<typeof AnonymousSessionSchema>;
