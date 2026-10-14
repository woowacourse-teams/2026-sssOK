export interface CreateRoomRequest {
  name: string;
  uploadPolicy: "everyone" | "host";
}

export interface CreateRoomResponse {
  roomId: number;
  code: string;
  name: string;
  hostId: number;
  hostName: string;
  createdAt: string;
  expiresAt: string;
  uploadPolicy: "everyone" | "host";
}
