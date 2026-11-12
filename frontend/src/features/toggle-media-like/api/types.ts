import { z } from "zod";
export const MediaLikeResponseSchema = z.object({
  mediaId: z.number(),
  liked: z.boolean(),
  likeCount: z.number(),
});
export type MediaLikeResponse = z.infer<typeof MediaLikeResponseSchema>;
