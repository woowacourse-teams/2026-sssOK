import { API_PREFIX } from "../config";
import { MOCK_ROOM_CODES } from "./room";

const getRoom = (code: string) => fetch(`${API_PREFIX}/rooms/${code}`);
const authorization = { Authorization: "Bearer mock-access-token" };

describe("GET /rooms/{code} 목 핸들러", () => {
  it("활성 방은 200 과 ACTIVE 상태를 내려준다", async () => {
    const response = await getRoom(MOCK_ROOM_CODES.active);
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body.data.status).toBe("ACTIVE");
    expect(body.data.code).toBe(MOCK_ROOM_CODES.active);
  });

  it("만료된 방도 404 가 아니라 200 과 EXPIRED 상태로 내려준다", async () => {
    const response = await getRoom(MOCK_ROOM_CODES.expired);
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body.data.status).toBe("EXPIRED");
  });

  it("삭제된 방도 200 과 DELETED 상태로 내려준다", async () => {
    const response = await getRoom(MOCK_ROOM_CODES.deleted);
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body.data.status).toBe("DELETED");
  });

  it("존재하지 않는 방은 404 ROOM_NOT_FOUND 로 내려준다", async () => {
    const response = await getRoom(MOCK_ROOM_CODES.notFound);
    const body = await response.json();

    expect(response.status).toBe(404);
    expect(body.code).toBe("ROOM_NOT_FOUND");
  });

  it("형식이 틀린 코드는 400 INVALID_ROOM_CODE 로 내려준다", async () => {
    const response = await getRoom(MOCK_ROOM_CODES.invalid);
    const body = await response.json();

    expect(response.status).toBe(400);
    expect(body.code).toBe("INVALID_ROOM_CODE");
  });

  it("방 조회 응답은 data 로 한 겹 감싸여 온다", async () => {
    const body = await (await getRoom(MOCK_ROOM_CODES.active)).json();

    expect(body).toHaveProperty("data");
    expect(body.data).toHaveProperty("roomId");
    expect(body.data).toHaveProperty("joined");
  });

  it("사진 수와 폴더 목록을 내려준다", async () => {
    const body = await (await getRoom(MOCK_ROOM_CODES.active)).json();

    expect(body.data.photoCount).toBe(13);
    expect(body.data.folders).toEqual([
      expect.objectContaining({ id: 501, name: "첫째 날", photoCount: 4 }),
    ]);
  });
});

describe("GET /rooms/{roomId}/media 목 핸들러", () => {
  it("인증된 요청에 전체 미디어 목록을 내려준다", async () => {
    const response = await fetch(`${API_PREFIX}/rooms/5031/media`, {
      headers: authorization,
    });
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body.data.items).toHaveLength(13);
    expect(body.data.items[0]).toEqual(
      expect.objectContaining({
        mediaId: 5012,
        type: "IMAGE",
        uploaderId: 10234,
        uploaderName: "로지",
      }),
    );
    expect(body.data).toEqual({ items: expect.any(Array) });
  });

  it("토큰이 없으면 401을 내려준다", async () => {
    const response = await fetch(`${API_PREFIX}/rooms/5031/media`);
    const body = await response.json();

    expect(response.status).toBe(401);
    expect(body.code).toBe("UNAUTHORIZED");
  });
});

describe("PATCH /rooms/{roomId} 목 핸들러", () => {
  it("전달한 필드만 수정한다", async () => {
    const response = await fetch(`${API_PREFIX}/rooms/5031`, {
      method: "PATCH",
      headers: { ...authorization, "Content-Type": "application/json" },
      body: JSON.stringify({
        name: "제주 3박 4일",
        uploadPolicy: "host",
        expiryHours: 12,
      }),
    });
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body.data).toEqual(
      expect.objectContaining({
        roomId: 5031,
        name: "제주 3박 4일",
        status: "ACTIVE",
        uploadPolicy: "host",
        expiresAt: "2026-08-18T18:00:00.000Z",
      }),
    );
  });

  it("수정할 필드가 없으면 400을 내려준다", async () => {
    const response = await fetch(`${API_PREFIX}/rooms/5031`, {
      method: "PATCH",
      headers: { ...authorization, "Content-Type": "application/json" },
      body: JSON.stringify({}),
    });

    expect(response.status).toBe(400);
    expect((await response.json()).code).toBe("EMPTY_PATCH");
  });
});

describe("DELETE /rooms/{roomId} 목 핸들러", () => {
  it("방을 삭제하고 삭제 일정을 내려준다", async () => {
    const response = await fetch(`${API_PREFIX}/rooms/5031`, {
      method: "DELETE",
      headers: authorization,
    });
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body.data).toEqual({
      code: MOCK_ROOM_CODES.active,
      status: "DELETED",
      deletedAt: "2026-08-18T07:00:00Z",
      purgeAt: "2026-08-25T07:00:00Z",
    });

    const roomResponse = await getRoom(MOCK_ROOM_CODES.active);
    expect((await roomResponse.json()).data.status).toBe("DELETED");
  });
});
