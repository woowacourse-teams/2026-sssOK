import type { Room } from "../model/types";

/**
 * 이 사람이 이 방에 사진을 올릴 수 있는지.
 *
 * 서버가 발급 시점에 보는 규칙과 같다 (`IssueUploadUrlsService`) — 어긋나면 올릴 수 있는
 * 것처럼 버튼을 내주고 403(`UPLOAD_NOT_ALLOWED`)으로 되돌아온다.
 *
 * 방에 들어왔는지는 여기서 보지 않는다. 그건 입장 화면이 이미 가른 것이고,
 * 여기 섞으면 "정책 때문에 못 올린다" 와 "입장을 안 했다" 를 한 값으로 뭉개게 된다.
 */
export const canUploadTo = (room: Pick<Room, "uploadPolicy" | "hostId">, userId: number) =>
  room.uploadPolicy === "everyone" || room.hostId === userId;
