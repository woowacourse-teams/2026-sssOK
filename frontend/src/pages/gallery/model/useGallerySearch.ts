import { useMemo, useState } from "react";

import type { GalleryItem } from "@/entities/media";
import { useMediaSearchQuery } from "@/features/search-media";
import { isApiError } from "@/shared/api";

interface UseGallerySearchParams {
  roomId: number;
  accessToken: string;
  userId: number;
  /** 검색하지 않을 때 보여줄 사진. 폴더, 업로더 필터가 적용된 목록이다. */
  items: GalleryItem[];
  /** 검색 결과를 고를 방 전체 사진. 검색은 필터와 상관없이 방 전체에서 찾는다. */
  allItems: GalleryItem[];
}

// 헤더 검색창의 상태와 결과
export const useGallerySearch = ({
  roomId,
  accessToken,
  userId,
  items,
  allItems,
}: UseGallerySearchParams) => {
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [query, setQuery] = useState("");
  const searchQuery = useMediaSearchQuery({ roomId, token: accessToken, userId, query });
  const isSearching = query.length > 0;

  const searchedItems = useMemo(() => {
    if (!isSearching) return items;
    if (!searchQuery.data) return [];

    // 서버가 유사도 높은 순으로 내려주므로 응답 순서가 곧 순위다.
    const rankById = new Map(searchQuery.data.items.map((media, index) => [media.mediaId, index]));

    return allItems
      .filter((item) => rankById.has(item.mediaId))
      .sort((a, b) => (rankById.get(a.mediaId) ?? 0) - (rankById.get(b.mediaId) ?? 0));
  }, [isSearching, items, allItems, searchQuery.data]);

  const errorMessage =
    isApiError(searchQuery.error) && searchQuery.error.code === "IMAGE_SEARCH_UNAVAILABLE"
      ? "지금은 사진 검색을 쓸 수 없어요."
      : "사진을 검색하지 못했어요. 다시 시도해 주세요.";

  return {
    isSearchOpen,
    isSearching,
    query,
    items: searchedItems,
    isPending: isSearching && searchQuery.isPending,
    isError: isSearching && searchQuery.isError,
    errorMessage,
    openSearch: () => setIsSearchOpen(true),
    search: setQuery,
    closeSearch: () => {
      setIsSearchOpen(false);
      setQuery("");
    },
  };
};
