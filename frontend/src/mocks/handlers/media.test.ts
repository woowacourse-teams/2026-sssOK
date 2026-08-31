import { getMedia, getPhotos } from "@/entities/media";
import { deleteMedia, deleteMediaBatch } from "@/features/delete-media";
import { API_BASE_URL } from "@/shared/config";
import { MOCK_ROOM_CODES, MOCK_ROOM_ID } from "./room";

const HOST = "mock-token-10234";
const MEMBER = "mock-token-12";
const join = (token: string) =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });

describe("미디어 단일 조회·삭제 및 다중 삭제 목", () => {
  beforeEach(() => localStorage.clear());

  it("인증하지 않았거나 입장하지 않은 사용자는 거절한다", async () => {
    const response = await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/media/5012`);
    expect(response.status).toBe(401);
    await expect(
      getMedia({ roomId: MOCK_ROOM_ID, mediaId: 5012, token: MEMBER }),
    ).rejects.toMatchObject({ code: "NOT_ROOM_MEMBER" });
  });

  it("단일 조회가 촬영 정보와 사용자별 삭제 권한을 반환한다", async () => {
    await join(MEMBER);
    const other = await getMedia({ roomId: MOCK_ROOM_ID, mediaId: 5012, token: MEMBER });
    expect(other).toMatchObject({
      mediaId: 5012,
      canDelete: false,
      takenAt: "2026-08-17T14:02:11+09:00",
      location: { name: "부산 해운대구" },
    });
    expect(other).not.toHaveProperty("thumbnailUrl");
    const own = await getMedia({ roomId: MOCK_ROOM_ID, mediaId: 5006, token: MEMBER });
    expect(own.canDelete).toBe(true);
    await join(HOST);
    const host = await getMedia({ roomId: MOCK_ROOM_ID, mediaId: 5006, token: HOST });
    expect(host.canDelete).toBe(true);
  });

  it("단일 삭제 후 목록에서 빠지고 다시 조회하면 404를 반환한다", async () => {
    await join(MEMBER);
    await expect(
      deleteMedia({ roomId: MOCK_ROOM_ID, mediaId: 5006, token: MEMBER }),
    ).resolves.toBeUndefined();
    const list = await getPhotos({ roomId: MOCK_ROOM_ID, token: MEMBER });
    expect(list.items.some((item) => item.mediaId === 5006)).toBe(false);
    await expect(
      getMedia({ roomId: MOCK_ROOM_ID, mediaId: 5006, token: MEMBER }),
    ).rejects.toMatchObject({ code: "MEDIA_NOT_FOUND" });
    const response = await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_CODES.active}`);
    const { data } = await response.json();
    expect(data.photoCount).toBe(15);
    expect(data.folders.find((folder: { id: number }) => folder.id === 32).photoCount).toBe(3);
  });

  it("권한 없는 단일 삭제는 사진을 유지한다", async () => {
    await join(MEMBER);
    await expect(
      deleteMedia({ roomId: MOCK_ROOM_ID, mediaId: 5012, token: MEMBER }),
    ).rejects.toMatchObject({ code: "MEDIA_FORBIDDEN" });
    await expect(
      getMedia({ roomId: MOCK_ROOM_ID, mediaId: 5012, token: MEMBER }),
    ).resolves.toMatchObject({ mediaId: 5012 });
  });

  it("다중 삭제는 중복을 한 번만 처리하고 권한 없는 항목과 없는 항목은 건너뛴다", async () => {
    await join(MEMBER);
    const result = await deleteMediaBatch({
      roomId: MOCK_ROOM_ID,
      mediaIds: [5006, 5006, 5012, 999999],
      token: MEMBER,
    });
    expect(result).toMatchObject({
      deleted: [5006],
      deletedCount: 1,
      skipped: [
        { mediaId: 5012, code: "MEDIA_FORBIDDEN" },
        { mediaId: 999999, code: "MEDIA_NOT_FOUND" },
      ],
    });
    await expect(
      getMedia({ roomId: MOCK_ROOM_ID, mediaId: 5006, token: MEMBER }),
    ).rejects.toMatchObject({ code: "MEDIA_NOT_FOUND" });
  });

  it("빈 다중 삭제 목록은 거절한다", async () => {
    await join(HOST);
    await expect(
      deleteMediaBatch({ roomId: MOCK_ROOM_ID, mediaIds: [], token: HOST }),
    ).rejects.toMatchObject({ code: "INVALID_MEDIA_IDS" });
  });
});
