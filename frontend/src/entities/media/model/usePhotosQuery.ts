import { useQuery } from "@tanstack/react-query";

import { getPhotos } from "../api/getPhotos";
import type { MediaList } from "./types";

interface UsePhotosQueryParams {
  roomId: number;
  token: string;
  userId: number;
}

/** 서명 URL 이 실제로 끊기기 전에 여유를 두고 새로 받는다. */
const THUMBNAIL_REFRESH_MARGIN_MS = 60 * 1000;
/** 만료가 코앞이어도 이보다 자주 부르지는 않는다. */
const MIN_REFRESH_INTERVAL_MS = 30 * 1000;

/**
 * 목록을 다시 불러와야 하는 쪽(업로드 직후 등)도 같은 키를 써야 한다.
 * 키를 손으로 다시 적으면 한쪽만 바뀌었을 때 갱신이 조용히 안 먹는다.
 */
export const photosQueryKey = (roomId: number, userId: number) => ["photos", roomId, userId];

/**
 * 썸네일 서명 URL 중 가장 먼저 끊기는 것에 맞춰 다음 조회 시점을 잡는다.
 * 목록을 한 번에 받으니 페이지를 넘기며 새 URL 을 받던 기회가 없다.
 * 그대로 두면 오래 켜 둔 갤러리의 썸네일이 한꺼번에 깨진다.
 */
export const untilThumbnailsExpire = (data: MediaList | undefined, now = Date.now()) => {
  const expiries = (data?.items ?? [])
    .map((item) => (item.thumbnailUrlExpiresAt ? Date.parse(item.thumbnailUrlExpiresAt) : NaN))
    .filter((time) => Number.isFinite(time));

  if (expiries.length === 0) return false;

  return Math.max(
    Math.min(...expiries) - now - THUMBNAIL_REFRESH_MARGIN_MS,
    MIN_REFRESH_INTERVAL_MS,
  );
};

export const usePhotosQuery = ({ roomId, token, userId }: UsePhotosQueryParams) =>
  // userId로 사용자별 캐시를 나누므로 인증 정보인 token은 queryKey에 노출하지 않는다.
  // eslint-disable-next-line @tanstack/query/exhaustive-deps
  useQuery({
    queryKey: photosQueryKey(roomId, userId),
    queryFn: () => getPhotos({ roomId, token }),
    refetchInterval: (query) => untilThumbnailsExpire(query.state.data),
  });
