import { apiClient } from "@/shared/api";
import { readValidRoomSession } from "../lib/roomSession";

/** 만료·삭제도 조회 자체는 성공한다. 화면 분기는 이 값을 따른다. */
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

/** 방 조회 응답만 data 로 한 겹 감싸여 온다. */
interface RoomResponse {
  data: Room;
}

/**
 * 공유 링크·QR 로 들어온 코드로 방을 조회한다. 입장 여부는 status 로 판단한다.
 * 이 방 토큰이 있으면 실어 보낸다 — 그래야 응답의 joined 가 채워진다.
 */
export const getRoom = async (code: string) => {
  const response = await apiClient<RoomResponse>(`/rooms/${code}`, {
    token: readValidRoomSession(code)?.accessToken,
  });

  return response.data;
};
