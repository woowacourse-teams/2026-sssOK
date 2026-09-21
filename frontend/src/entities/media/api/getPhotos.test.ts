import { getPhotos } from "./getPhotos";

const request = {
  roomId: 5031,
  token: "mock-token-10234",
} as const;

describe("getPhotos", () => {
  it("첫 페이지와 다음 페이지를 받을 커서를 조회한다", async () => {
    const result = await getPhotos(request);

    expect(result.items).toHaveLength(30);
    expect(result).toEqual({
      items: expect.any(Array),
      nextCursor: expect.any(String),
      hasNext: true,
      totalCount: 33,
    });
  });

  it("받은 커서로 다음 페이지를 이어 받는다", async () => {
    const first = await getPhotos(request);
    const second = await getPhotos({ ...request, cursor: first.nextCursor ?? undefined });

    expect(second.items[0].mediaId).toBeLessThan(first.items[first.items.length - 1].mediaId);
    expect(second.hasNext).toBe(false);
  });

  it("폴더와 업로더 필터를 쿼리로 보낸다", async () => {
    const result = await getPhotos({ ...request, folderId: 32, uploader: "OTHERS" });

    expect(result.items.map((item) => item.mediaId)).toEqual([5006]);
    expect(result.totalCount).toBe(1);
  });
});
