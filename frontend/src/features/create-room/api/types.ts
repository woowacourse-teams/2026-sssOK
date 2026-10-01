export interface CreateRoomRequest {
  name: string;
  uploadPolicy: "everyone" | "host";
  /** 1~14일 */
  expiryDays: number;
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
