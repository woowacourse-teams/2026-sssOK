import { MOCK_FOLDER_IDS, MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { editFolder } from "./editFolder";

describe("editFolder", () => {
  it("선택한 폴더 이름을 수정한다", async () => {
    const folder = await editFolder({
      roomId: MOCK_ROOM_ID,
      folderId: MOCK_FOLDER_IDS[0],
      accessToken: "mock-token",
      name: "여름",
    });

    expect(folder).toMatchObject({ id: MOCK_FOLDER_IDS[0], name: "여름", photoCount: 12 });
  });
});
