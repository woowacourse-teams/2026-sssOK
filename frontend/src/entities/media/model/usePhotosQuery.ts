import { useInfiniteQuery } from "@tanstack/react-query";

import { getPhotos } from "../api/getPhotos";
import type { MediaUploaderFilter } from "./types";

interface UsePhotosQueryParams {
  roomId: number;
  token: string;
  userId: number;
  folderId?: number;
  uploader: MediaUploaderFilter;
}

/**
 * 목록을 다시 불러와야 하는 쪽(업로드 직후 등)도 같은 키를 써야 한다.
 * 키를 손으로 다시 적으면 한쪽만 바뀌었을 때 갱신이 조용히 안 먹는다.
 *
 * 필터별 목록은 이 키 **뒤에** 필터를 붙여 나눈다. 그래서 이 키로 invalidate 하면
 * 필터와 상관없이 방의 목록 전부가 갱신된다 — `exact: true` 를 붙이면 하나도 안 걸린다.
 */
export const photosQueryKey = (roomId: number, userId: number) => ["photos", roomId, userId];

export const usePhotosQuery = ({
  roomId,
  token,
  userId,
  folderId,
  uploader,
}: UsePhotosQueryParams) =>
  // userId로 사용자별 캐시를 나누므로 인증 정보인 token은 queryKey에 노출하지 않는다.
  // eslint-disable-next-line @tanstack/query/exhaustive-deps
  useInfiniteQuery({
    // 필터를 키에 넣는다. 서버 커서는 필터에 묶여 있어서, 필터가 바뀌었는데 같은 쿼리로
    // 이어 부르면 이전 커서가 실려 400 INVALID_CURSOR 가 난다. 키가 다르면 첫 페이지부터 받는다.
    queryKey: [...photosQueryKey(roomId, userId), { folderId: folderId ?? null, uploader }],
    queryFn: ({ pageParam }) => getPhotos({ roomId, token, cursor: pageParam, folderId, uploader }),
    // 첫 페이지는 커서 없이 부른다 — 서버가 최신부터 내려준다.
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => {
      // hasNext 와 nextCursor 를 둘 다 본다. 서버가 hasNext 만 true 로 주고 커서를
      // 빠뜨리면 같은 페이지를 무한히 다시 부르게 된다.
      if (!lastPage.hasNext || lastPage.nextCursor === null) return undefined;

      return lastPage.nextCursor;
    },
  });
