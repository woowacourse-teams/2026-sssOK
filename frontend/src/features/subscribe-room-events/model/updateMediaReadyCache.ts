import type { QueryClient } from "@tanstack/react-query";

import { photosQueryKey, type MediaItem, type MediaList } from "@/entities/media";

interface Params {
  queryClient: QueryClient;
  roomId: number;
  userId: number;
  media: MediaItem;
  replacedPending: boolean;
}

export const updateMediaReadyCache = ({
  queryClient,
  roomId,
  userId,
  media,
  replacedPending,
}: Params) => {
  queryClient.setQueryData<MediaList>(photosQueryKey(roomId, userId), (current) => {
    if (!current) return current;

    const exists = current.items.some((item) => item.mediaId === media.mediaId);
    if (exists) {
      return {
        ...current,
        items: current.items.map((item) => (item.mediaId === media.mediaId ? media : item)),
      };
    }
    // 내가 올린 것은 미리보기 자리가 이미 있다. 목록에 또 넣으면 두 장으로 보인다.
    if (replacedPending) return current;

    return { ...current, items: [media, ...current.items] };
  });
};
