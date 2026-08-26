import { API_BASE_URL } from "@/shared/config";
import { MOCK_ROOM_CODES, MOCK_ROOM_ID } from "./room";

const TOKEN = "Bearer mock-token-10234";

const getRoom = (code: string, token?: string) =>
  fetch(`${API_BASE_URL}/rooms/${code}`, token ? { headers: { Authorization: token } } : undefined);

const joinRoom = (token: string, roomId: number = MOCK_ROOM_ID) =>
  fetch(`${API_BASE_URL}/rooms/${roomId}/members`, {
    method: "POST",
    headers: { Authorization: token },
  });

const roomIdOf = async (code: string) => (await (await getRoom(code)).json()).data.roomId as number;

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

  it("방마다 업로드 권한이 다르다 — 방장 전용 방은 uploadPolicy 가 host 다", async () => {
    const anyone = await (await getRoom(MOCK_ROOM_CODES.active)).json();
    const hostOnly = await (await getRoom(MOCK_ROOM_CODES.hostOnly)).json();

    expect(anyone.data.uploadPolicy).toBe("everyone");
    expect(hostOnly.data.uploadPolicy).toBe("host");
  });

  it("방 조회 응답은 data 로 한 겹 감싸여 온다", async () => {
    const body = await (await getRoom(MOCK_ROOM_CODES.active)).json();

    expect(body).toHaveProperty("data");
    expect(body.data).toHaveProperty("roomId");
    expect(body.data).toHaveProperty("joined");
  });

  it("입장 전에는 토큰을 실어도 joined 가 false 다", async () => {
    const body = await (await getRoom(MOCK_ROOM_CODES.active, TOKEN)).json();

    expect(body.data.joined).toBe(false);
  });

  it("입장한 뒤 같은 토큰으로 조회하면 joined 가 true 다", async () => {
    await joinRoom(TOKEN);

    const body = await (await getRoom(MOCK_ROOM_CODES.active, TOKEN)).json();

    expect(body.data.joined).toBe(true);
  });

  it("입장 기록은 토큰마다 따로다 — 다른 토큰은 joined 가 false 다", async () => {
    await joinRoom(TOKEN);

    const body = await (await getRoom(MOCK_ROOM_CODES.active, "Bearer other-token")).json();

    expect(body.data.joined).toBe(false);
  });

  it("입장했어도 토큰 없는 조회는 joined 가 false 다", async () => {
    await joinRoom(TOKEN);

    const body = await (await getRoom(MOCK_ROOM_CODES.active)).json();

    expect(body.data.joined).toBe(false);
  });

  it("방마다 번호가 다르다 — 시나리오 코드끼리 roomId 가 겹치지 않는다", async () => {
    const codes = [
      MOCK_ROOM_CODES.active,
      MOCK_ROOM_CODES.second,
      MOCK_ROOM_CODES.expired,
      MOCK_ROOM_CODES.deleted,
    ];

    const roomIds = await Promise.all(codes.map(roomIdOf));

    expect(new Set(roomIds).size).toBe(codes.length);
  });

  it("입장 기록은 방마다 따로다 — 다른 방에 입장해도 이 방은 joined 가 false 다", async () => {
    await joinRoom(TOKEN, await roomIdOf(MOCK_ROOM_CODES.second));

    const body = await (await getRoom(MOCK_ROOM_CODES.active, TOKEN)).json();

    expect(body.data.joined).toBe(false);
  });

  it("입장한 방만 joined 가 true 다", async () => {
    const secondRoomId = await roomIdOf(MOCK_ROOM_CODES.second);

    await joinRoom(TOKEN, secondRoomId);

    const body = await (await getRoom(MOCK_ROOM_CODES.second, TOKEN)).json();

    expect(body.data.joined).toBe(true);
  });
});

describe("POST /rooms/{roomId}/members 목 핸들러", () => {
  it("처음 입장은 201, 다시 입장하면 200 으로 멱등하게 응답한다", async () => {
    expect((await joinRoom(TOKEN)).status).toBe(201);
    expect((await joinRoom(TOKEN)).status).toBe(200);
  });

  it("토큰이 없으면 401 UNAUTHORIZED 로 내려준다", async () => {
    const response = await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
      method: "POST",
    });
    const body = await response.json();

    expect(response.status).toBe(401);
    expect(body.code).toBe("UNAUTHORIZED");
  });
});
