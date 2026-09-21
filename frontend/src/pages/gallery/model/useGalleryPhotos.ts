import { useCallback, useMemo } from "react";

import { usePhotosQuery } from "@/entities/media";
import type { MediaUploaderFilter, PhotoFilter } from "@/entities/media";

interface UseGalleryPhotosParams {
  roomId: number;
  accessToken: string;
  userId: number;
  selectedFolderId: number | null;
  selectedOption: PhotoFilter;
}

const UPLOADER_OF: Record<PhotoFilter, MediaUploaderFilter> = {
  all: "ALL",
  mine: "ME",
  others: "OTHERS",
};

/**
 * 갤러리에 그릴 사진을 페이지 단위로 이어 받는다.
 *
 * 폴더·업로더 필터는 서버에 맡긴다. 받아 온 페이지 안에서 거르면 방 전체가 아니라
 * 지금까지 받은 사진 안에서만 걸러진다 — 오래된 사진만 든 폴더가 비어 보인다 (#264).
 */
export const useGalleryPhotos = ({
  roomId,
  accessToken,
  userId,
  selectedFolderId,
  selectedOption,
}: UseGalleryPhotosParams) => {
  const { data, isPending, isError, hasNextPage, isFetchingNextPage, fetchNextPage } =
    usePhotosQuery({
      roomId,
      token: accessToken,
      userId,
      folderId: selectedFolderId ?? undefined,
      uploader: UPLOADER_OF[selectedOption],
    });

  const photos = useMemo(
    () =>
      (data?.pages.flatMap((page) => page.items) ?? []).filter(
        (photo) => typeof photo.thumbnailUrl === "string" && photo.thumbnailUrl.trim().length > 0,
      ),
    [data],
  );

  const loadMore = useCallback(() => {
    void fetchNextPage();
  }, [fetchNextPage]);

  return {
    photos,
    // 페이지마다 그 시점의 개수가 온다. 가장 최근에 받은 값이 실제에 가깝다.
    totalCount: data?.pages[data.pages.length - 1]?.totalCount,
    isPending,
    isError,
    hasNextPage,
    isFetchingNextPage,
    loadMore,
  };
};
