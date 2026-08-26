import { MOCK_ROOM_CODES } from "@/mocks/handlers/room";
import { getRoom } from "./getRoom";

describe("getRoom", () => {
  it("apiClient가 data를 해제한 방 정보를 반환한다", async () => {
    const room = await getRoom(MOCK_ROOM_CODES.active);

    expect(room.roomId).toBe(5031);
    expect(room.name).toBe("제주 여행");
    expect(room).not.toHaveProperty("data");
  });
});
