export interface CreateRoomRequest {
  /** 생략하면 계정 기본 이름을 사용한다. */
  nickname?: string;
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
