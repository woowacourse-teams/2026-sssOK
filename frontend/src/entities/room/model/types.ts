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
  photoCount: number;
  folders: RoomFolder[];
}
