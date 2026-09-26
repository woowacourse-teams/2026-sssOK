import { getPhotos } from "./getPhotos";

const request = {
  roomId: 5031,
  token: "mock-token-10234",
} as const;

describe("getPhotos", () => {
  it("방의 미디어를 페이지 없이 한 번에 전부 받는다", async () => {
    const result = await getPhotos(request);

    expect(result.items).toHaveLength(33);
    expect(result).toEqual({ items: expect.any(Array) });
  });
});
