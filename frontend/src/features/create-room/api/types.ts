import { z } from "zod";
export const CreateRoomResponseSchema = z.object({
  roomId: z.number(),
  code: z.string(),
  name: z.string(),
  hostId: z.number(),
  hostName: z.string(),
  createdAt: z.string(),
  expiresAt: z.string(),
  uploadPolicy: z.union([z.literal("everyone"), z.literal("host")]),
});
export interface CreateRoomRequest {
  /** 생략하면 계정 기본 이름을 사용한다. */
  nickname?: string;
  name: string;
  uploadPolicy: "everyone" | "host";
}

export type CreateRoomResponse = z.infer<typeof CreateRoomResponseSchema>;
