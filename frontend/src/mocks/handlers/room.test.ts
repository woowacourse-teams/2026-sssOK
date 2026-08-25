import { MOCK_ROOM_CODES } from "./room";

const getRoom = (code: string) => fetch(`/rooms/${code}`);

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
});
