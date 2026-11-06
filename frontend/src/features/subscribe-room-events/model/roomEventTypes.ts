import type { RoomFolder } from "@/entities/room";

export interface MediaFoldersUpdatedEvent {
  roomId: number;
  action: "ADD" | "REMOVE";
  mediaIds: number[];
  folders: Pick<RoomFolder, "id" | "name" | "photoCount">[];
}

export interface MediaLikesUpdatedEvent {
  roomId: number;
  mediaId: number;
  likeCount: number;
}

/** `organize.room.changed`. 방에 진행 중인 사진 정리가 있는지. 방 멤버 모두가 받는다. */
export interface OrganizeRoomChangedEvent {
  roomId: number;
  roomVersion: number;
  busy: boolean;
}

/**
 * `organize.job.updated`. 정리를 시작한 사람만 받는다.
 * 필드 해석은 정리 기능(`organize-media`)이 맡고, 여기서는 받은 그대로 넘긴다.
 */
export interface OrganizeJobUpdatedEvent {
  jobId: string;
  jobVersion: number;
  status: string;
  [field: string]: unknown;
}

export interface UploadRequestedEvent {
  roomId: number;
  roomCode: string;
  requesterId: number;
  title: string;
  body: string;
}
