export type RoomStatus = "ACTIVE" | "EXPIRED" | "DELETED" | "PURGED";

export interface RoomFolder {
  id: number;
  name: string;
  createdAt: string;
  photoCount: number;
}

export interface Room {
  roomId: number;
  code: string;
  name: string;
  status: RoomStatus;
  hostId: number;
  hostName: string;
  createdAt: string;
  expiresAt: string;
  uploadPolicy: "everyone" | "host";
  joined: boolean;
  myNickname?: string | null;
  photoCount: number;
  folders: RoomFolder[];
}

export interface MyRoom {
  roomId: number;
  code: string;
  name: string;
  isHost: boolean;
  mediaCount: number;
  memberCount: number;
  thumbnailUrl: string | null;
}
