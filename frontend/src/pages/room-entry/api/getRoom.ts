import { apiClient } from "@/shared/api";

/** backend RoomStatus 와 같은 값. 만료·삭제도 조회 자체는 성공한다. */
export type RoomStatus = "ACTIVE" | "EXPIRED" | "DELETED";

export interface Room {
  roomId: number;
  code: string;
  name: string;
  status: RoomStatus;
  hostId: number;
  hostName: string;
  uploadPolicy: "everyone" | "host";
  /** 요청에 토큰이 실렸을 때만 의미가 있다. 비로그인은 항상 false 다. */
  joined: boolean;
  expiresAt: string;
  createdAt: string;
}

/** 공유 링크·QR 로 들어온 코드로 방을 조회한다. 입장에 필요한 roomId 를 여기서 얻는다. */
export const getRoom = (code: string) => apiClient<Room>(`/rooms/${code}`);
