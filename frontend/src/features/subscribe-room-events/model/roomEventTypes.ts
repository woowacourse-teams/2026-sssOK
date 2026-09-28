import type { RoomFolder } from "@/entities/room";

export interface MediaFoldersUpdatedEvent {
  roomId: number;
  action: "ADD" | "REMOVE";
  mediaIds: number[];
  folders: Pick<RoomFolder, "id" | "name" | "photoCount">[];
}
