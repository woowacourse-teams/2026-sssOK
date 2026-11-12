import { z } from "zod";
export const MyRoomSchema = z.object({
  roomId: z.number(),
  code: z.string(),
  name: z.string(),
  isHost: z.boolean(),
  mediaCount: z.number(),
  memberCount: z.number(),
  thumbnailUrl: z.union([z.string(), z.null()]),
});
export const RoomStatusSchema = z.union([
  z.literal("ACTIVE"),
  z.literal("EXPIRED"),
  z.literal("DELETED"),
  z.literal("PURGED"),
]);
export const RoomFolderSchema = z.object({
  id: z.number(),
  name: z.string(),
  createdAt: z.string(),
  photoCount: z.number(),
});
export const RoomSchema = z.object({
  roomId: z.number(),
  code: z.string(),
  name: z.string(),
  status: RoomStatusSchema,
  hostId: z.number(),
  hostName: z.string(),
  createdAt: z.string(),
  expiresAt: z.string(),
  uploadPolicy: z.union([z.literal("everyone"), z.literal("host")]),
  joined: z.boolean(),
  myNickname: z.union([z.string(), z.null()]).optional(),
  photoCount: z.number(),
  folders: z.array(RoomFolderSchema),
});
export type RoomStatus = z.infer<typeof RoomStatusSchema>;

export type RoomFolder = z.infer<typeof RoomFolderSchema>;

export type Room = z.infer<typeof RoomSchema>;

export type MyRoom = z.infer<typeof MyRoomSchema>;
