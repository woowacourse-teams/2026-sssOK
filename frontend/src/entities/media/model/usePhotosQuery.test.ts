import type { MediaItem, MediaList } from "./types";
import { shouldRefetchOnFocus, untilThumbnailsExpire } from "./usePhotosQuery";

const NOW = Date.parse("2026-09-25T12:00:00Z");

const listOf = (...expiries: (string | null | undefined)[]): MediaList => ({
  items: expiries.map(
    (thumbnailUrlExpiresAt, index) => ({ mediaId: index + 1, thumbnailUrlExpiresAt }) as MediaItem,
  ),
});

describe("untilThumbnailsExpire", () => {
  it("가장 먼저 끊기는 썸네일보다 1분 앞서 다시 받는다", () => {
    const data = listOf("2026-09-25T12:30:00Z", "2026-09-25T12:10:00Z");

    expect(untilThumbnailsExpire(data, NOW)).toBe(9 * 60 * 1000);
  });

  it("만료가 코앞이어도 30초보다 자주 부르지 않는다", () => {
    expect(untilThumbnailsExpire(listOf("2026-09-25T12:00:10Z"), NOW)).toBe(30 * 1000);
  });

  it("만료 시각을 모르면 주기적으로 부르지 않는다", () => {
    expect(untilThumbnailsExpire(listOf(null, undefined), NOW)).toBe(false);
    expect(untilThumbnailsExpire(undefined, NOW)).toBe(false);
  });
});

describe("shouldRefetchOnFocus", () => {
  it("썸네일이 아직 넉넉하면 화면에 돌아와도 다시 받지 않는다", () => {
    expect(shouldRefetchOnFocus(listOf("2026-09-25T12:10:00Z"), NOW)).toBe(false);
  });

  it("가장 먼저 끊기는 썸네일이 1분 안에 끊기면 다시 받는다", () => {
    expect(shouldRefetchOnFocus(listOf("2026-09-25T12:30:00Z", "2026-09-25T12:00:30Z"), NOW)).toBe(
      true,
    );
  });

  it("이미 끊긴 뒤에 돌아와도 다시 받는다", () => {
    expect(shouldRefetchOnFocus(listOf("2026-09-25T11:50:00Z"), NOW)).toBe(true);
  });

  it("만료 시각을 모르면 다시 받지 않는다", () => {
    expect(shouldRefetchOnFocus(listOf(null), NOW)).toBe(false);
    expect(shouldRefetchOnFocus(undefined, NOW)).toBe(false);
  });
});
