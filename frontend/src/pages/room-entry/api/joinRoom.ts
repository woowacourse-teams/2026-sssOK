import { apiClient } from "@/shared/api";

export interface RoomMember {
  roomId: number;
  userId: number;
  /** 인증 시점의 닉네임. 이 방에서 다른 사람에게 보이는 이름이다. */
  displayName: string;
  hostId: number;
  /** 최초 입장 시각. 다시 입장해도 바뀌지 않는다. */
  joinedAt: string;
}

/**
 * 방에 참여 기록을 남긴다. 멱등이라 이미 입장했으면 201 대신 200 으로 같은 내용을 돌려준다.
 * 토큰 발급만으로는 방 멤버가 되지 않는다 — 이걸 불러야 조회 응답의 joined 가 채워지고,
 * 구독·업로드도 열린다.
 */
export const joinRoom = (roomId: number, token: string) =>
  apiClient<RoomMember>(`/rooms/${roomId}/members`, { method: "POST", token });
