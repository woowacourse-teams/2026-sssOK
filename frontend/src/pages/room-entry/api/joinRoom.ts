import { apiClient } from "@/shared/api";

export interface JoinRoomRequest {
  /** 생략하면 계정 기본 이름을 사용한다. */
  nickname?: string;
}

export interface RoomMember {
  roomId: number;
  userId: number;
  /** 이 방에서 다른 사람에게 보이는 닉네임. */
  displayName: string;
  hostId: number;
  /** 최초 입장 시각. 다시 입장해도 바뀌지 않는다. */
  joinedAt: string;
}

/**
 * 방에 참여 기록을 남긴다. 멱등이라 이미 입장했으면 201 대신 200 으로 같은 내용을 돌려준다.
 * 토큰 발급만으로는 방 멤버가 되지 않는다 — 이걸 불러야 조회 응답의 joined 가 채워지고,
 * 구독·업로드도 열린다. 경로에는 방 코드가 아니라 방 조회 응답의 roomId 를 쓴다.
 */
export const joinRoom = (roomId: number, token: string, request?: JoinRoomRequest) =>
  apiClient<RoomMember>(`/rooms/${roomId}/members`, {
    method: "POST",
    token,
    headers: { "Content-Type": "application/json" },
    body: request === undefined ? undefined : JSON.stringify(request),
  });
