import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { createFolder } from "./createFolder";

describe("createFolder", () => {
  it("이름을 보내 새 폴더를 만든다", async () => {
    const folder = await createFolder({
      roomId: MOCK_ROOM_ID,
      accessToken: "mock-token",
      name: "셋째 날",
    });

    expect(folder).toMatchObject({ name: "셋째 날", photoCount: 0 });
    expect(folder.id).toEqual(expect.any(Number));
  });

  it("토큰이 없으면 생성하지 못한다", async () => {
    await expect(
      createFolder({ roomId: MOCK_ROOM_ID, accessToken: "", name: "셋째 날" }),
    ).rejects.toMatchObject({ status: 401, code: "UNAUTHORIZED" });
  });
});
