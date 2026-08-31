import { MOCK_FOLDER_IDS, MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { deleteFolder } from "./deleteFolder";

describe("deleteFolder", () => {
  it("선택한 폴더만 삭제하고 분리된 사진 수를 반환한다", async () => {
    const result = await deleteFolder({
      roomId: MOCK_ROOM_ID,
      folderId: MOCK_FOLDER_IDS[0],
      accessToken: "mock-token",
    });

    expect(result).toEqual({ deletedFolderId: MOCK_FOLDER_IDS[0], detachedPhotoCount: 12 });
  });
});
