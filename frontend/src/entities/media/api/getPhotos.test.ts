import { getPhotos } from "./getPhotos";

const request = {
  roomId: 5031,
  token: "mock-token-10234",
} as const;

describe("getPhotos", () => {
  it("전체 사진을 조회한다", async () => {
    const result = await getPhotos(request);

    expect(result.items).toHaveLength(13);
    expect(result).toEqual({ items: expect.any(Array) });
  });
});
