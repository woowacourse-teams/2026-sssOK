import { getPhotos } from "./getPhotos";

const request = {
  roomId: 5031,
  token: "mock-token-10234",
} as const;

describe("getPhotos", () => {
  it("첫 페이지를 조회한다", async () => {
    const result = await getPhotos(request);

    expect(result.items).toHaveLength(30);
    expect(result).toMatchObject({ hasNext: true, totalCount: 33 });
  });
});
