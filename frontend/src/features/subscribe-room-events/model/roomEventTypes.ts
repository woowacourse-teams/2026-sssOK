import { z } from "zod";
import { MediaItemSchema } from "@/entities/media/model/types";

// backend MediaDetail은 SSE에서 사용자별 likedByMe를 @JsonIgnore로 제외한다.
export const MediaReadyEventSchema = MediaItemSchema.omit({ likedByMe: true });
export type MediaReadyEvent = z.infer<typeof MediaReadyEventSchema>;
import { RoomFolderSchema } from "../../../entities/room/model/types";
export const MediaFoldersUpdatedEventSchema = z.object({
  roomId: z.number(),
  action: z.union([z.literal("ADD"), z.literal("REMOVE")]),
  mediaIds: z.array(z.number()),
  folders: z.array(RoomFolderSchema.pick({ id: true, name: true, photoCount: true })),
});
export const MediaLikesUpdatedEventSchema = z.object({
  roomId: z.number(),
  mediaId: z.number(),
  likeCount: z.number(),
});
export const OrganizeRoomChangedEventSchema = z.object({
  roomId: z.number(),
  roomVersion: z.number(),
  busy: z.boolean(),
});
export const OrganizeJobUpdatedEventSchema = z
  .object({ jobId: z.string(), jobVersion: z.number(), status: z.string() })
  .catchall(z.unknown());
export const UploadRequestedEventSchema = z.object({
  roomId: z.number(),
  roomCode: z.string(),
  requesterId: z.number(),
  title: z.string(),
  body: z.string(),
});

export type MediaFoldersUpdatedEvent = z.infer<typeof MediaFoldersUpdatedEventSchema>;

export type MediaLikesUpdatedEvent = z.infer<typeof MediaLikesUpdatedEventSchema>;

/** `organize.room.changed`. 방에 진행 중인 사진 정리가 있는지. 방 멤버 모두가 받는다. */
export type OrganizeRoomChangedEvent = z.infer<typeof OrganizeRoomChangedEventSchema>;

/**
 * `organize.job.updated`. 정리를 시작한 사람만 받는다.
 * 필드 해석은 정리 기능(`organize-media`)이 맡고, 여기서는 받은 그대로 넘긴다.
 */
export type OrganizeJobUpdatedEvent = z.infer<typeof OrganizeJobUpdatedEventSchema>;

export type UploadRequestedEvent = z.infer<typeof UploadRequestedEventSchema>;
