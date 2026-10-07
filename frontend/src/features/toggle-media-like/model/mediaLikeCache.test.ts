import type { MediaItem, MediaList } from "@/entities/media";
import { setMyMediaLike } from "./mediaLikeCache";

const photos = {
  items: [{ mediaId: 1, likeCount: 7, likedByMe: false } as MediaItem],
} satisfies MediaList;

describe("mediaLikeCache", () => {
  it("내 좋아요를 낙관적으로 반영하되 같은 상태는 중복 반영하지 않는다", () => {
    const liked = setMyMediaLike(photos, 1, true);
    const duplicated = setMyMediaLike(liked, 1, true);

    expect(duplicated?.items[0]).toMatchObject({ likeCount: 8, likedByMe: true });
  });
});
