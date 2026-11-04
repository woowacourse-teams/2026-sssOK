import { useEffect, useMemo, useRef, useState } from "react";

import type { GalleryItem } from "@/entities/media";
import { type SearchType, type Uploader, useMediaSearchQuery } from "@/features/search-media";
import { isApiError } from "@/shared/api";
import { track } from "@/shared/lib";

interface UseGallerySearchParams {
  roomId: number;
  accessToken: string;
  userId: number;
  /** 검색하지 않을 때 보여줄 사진. 폴더, 업로더 필터가 적용된 목록이다. */
  items: GalleryItem[];
  /** 검색 결과를 고를 방 전체 사진. 검색은 필터와 상관없이 방 전체에서 찾는다. */
  allItems: GalleryItem[];
}

/** 결과를 기다리는 제출. 결과가 오면 이벤트를 보내고 비운다. */
interface PendingSubmission {
  query: string;
  startedAt: number;
}

const filterByNickname = (items: GalleryItem[], query: string) => {
  const keyword = query.toLowerCase();
  return items.filter(
    (item) =>
      item.type === "server" && (item.media.uploaderName ?? "").toLowerCase().includes(keyword),
  );
};

// 목록에서 고른 사람은 이름이 겹쳐도 그 사람 사진만 보여야 해서 id 로 거른다.
const filterByUploaderId = (items: GalleryItem[], uploaderId: number) =>
  items.filter((item) => item.type === "server" && item.media.uploaderId === uploaderId);

// 방에 사진을 올린 사람과 장수 -> 올린 사람으로 찾을 때 고를 수 있는 목록이다.
const collectUploaders = (items: GalleryItem[], userId: number): Uploader[] => {
  const uploaders = new Map<number, Uploader>();
  for (const item of items) {
    if (item.type !== "server" || !item.media.uploaderName) continue;
    const { uploaderName: name, uploaderId: id } = item.media;
    uploaders.set(id, {
      id,
      name,
      count: (uploaders.get(id)?.count ?? 0) + 1,
      isMe: id === userId,
    });
  }
  return [...uploaders.values()];
};

// 헤더 검색창의 상태와 결과
export const useGallerySearch = ({
  roomId,
  accessToken,
  userId,
  items,
  allItems,
}: UseGallerySearchParams) => {
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [searchType, setSearchType] = useState<SearchType>("keyword");
  const [query, setQuery] = useState("");
  // 요청 패널에서 고른 올린 사람. 이름을 직접 입력해 찾을 때는 null 이다.
  const [uploaderId, setUploaderId] = useState<number | null>(null);
  const [submitCount, setSubmitCount] = useState(0);
  const pendingRef = useRef<PendingSubmission | null>(null);
  const isKeywordSearch = searchType === "keyword";
  // 닉네임 검색은 받아 둔 목록에서 거르므로 서버를 부르지 않는다.
  const searchQuery = useMediaSearchQuery({
    roomId,
    token: accessToken,
    userId,
    query: isKeywordSearch ? query : "",
  });
  const isSearching = query.length > 0;
  // 꺼둔 쿼리도 데이터가 없으면 isPending 이 true 라, 검색어 검색일 때만 본다.
  const isKeywordSearching = isSearching && isKeywordSearch;

  const searchedItems = useMemo(() => {
    if (!isSearching) return items;
    if (!isKeywordSearch) {
      return uploaderId === null
        ? filterByNickname(allItems, query)
        : filterByUploaderId(allItems, uploaderId);
    }
    if (!searchQuery.data) return [];

    // 서버가 유사도 높은 순으로 내려주므로 응답 순서가 곧 순위다.
    const rankById = new Map(searchQuery.data.items.map((media, index) => [media.mediaId, index]));

    return allItems
      .filter((item) => rankById.has(item.mediaId))
      .sort((a, b) => (rankById.get(a.mediaId) ?? 0) - (rankById.get(b.mediaId) ?? 0));
  }, [isSearching, isKeywordSearch, query, uploaderId, items, allItems, searchQuery.data]);

  useEffect(() => {
    const pending = pendingRef.current;
    if (!pending || pending.query !== query) return;

    if (searchQuery.status === "success" && !searchQuery.isFetching) {
      pendingRef.current = null;
      track("Photo Searched", {
        search_type: "keyword",
        query_length: query.length,
        result_count: searchedItems.length,
        duration_ms: Math.round(performance.now() - pending.startedAt),
      });
    } else if (searchQuery.status === "error") {
      pendingRef.current = null;
      track("Photo Search Failed", {
        reason: isApiError(searchQuery.error) ? searchQuery.error.code : "REQUEST_FAILED",
      });
    }
  }, [
    submitCount,
    query,
    searchQuery.status,
    searchQuery.isFetching,
    searchQuery.error,
    searchedItems.length,
  ]);

  const uploaders = useMemo(() => collectUploaders(allItems, userId), [allItems, userId]);

  // 결과를 보는 동안 입력창에 남겨 둘 글
  const appliedText = isSearching ? query : null;

  const errorMessage =
    isApiError(searchQuery.error) && searchQuery.error.code === "IMAGE_SEARCH_UNAVAILABLE"
      ? "지금은 사진 검색을 쓸 수 없어요."
      : "사진을 검색하지 못했어요. 다시 시도해 주세요.";

  const resetQuery = () => {
    pendingRef.current = null;
    setQuery("");
    setUploaderId(null);
  };

  return {
    isSearchOpen,
    isSearching,
    appliedText,
    uploaders,
    searchType,
    query,
    items: searchedItems,
    isPending: isKeywordSearching && searchQuery.isPending,
    isError: isKeywordSearching && searchQuery.isError,
    errorMessage,
    openSearch: () => {
      setIsSearchOpen(true);
      track("Search Opened", {});
    },
    // 기준이 바뀌면 이전 기준의 결과는 의미가 없어서 검색어와 결과를 비운다.
    changeSearchType: (nextSearchType: SearchType) => {
      setSearchType(nextSearchType);
      resetQuery();
    },
    search: (nextQuery: string) => {
      setQuery(nextQuery);
      setUploaderId(null);

      if (!isKeywordSearch) {
        track("Photo Searched", {
          search_type: "nickname",
          query_length: nextQuery.length,
          result_count: filterByNickname(allItems, nextQuery).length,
        });
        return;
      }

      pendingRef.current = { query: nextQuery, startedAt: performance.now() };
      setSubmitCount((count) => count + 1);
    },

    searchBy: (nextSearchType: SearchType, nextQuery: string) => {
      setSearchType(nextSearchType);
      setQuery(nextQuery);
      setUploaderId(null);

      if (nextSearchType === "nickname") {
        track("Photo Searched", {
          search_type: "nickname",
          query_length: nextQuery.length,
          result_count: filterByNickname(allItems, nextQuery).length,
        });
        return;
      }

      pendingRef.current = { query: nextQuery, startedAt: performance.now() };
      setSubmitCount((count) => count + 1);
    },
    /** 요청 패널에서 고른 사람이 올린 사진만 찾는다. 입력창에는 그 사람 이름을 남긴다. */
    searchByUploader: (uploader: Pick<Uploader, "id" | "name">) => {
      pendingRef.current = null;
      setSearchType("nickname");
      setQuery(uploader.name);
      setUploaderId(uploader.id);
      track("Photo Searched", {
        search_type: "nickname",
        query_length: uploader.name.length,
        result_count: filterByUploaderId(allItems, uploader.id).length,
      });
    },
    closeSearch: () => {
      setIsSearchOpen(false);
      setSearchType("keyword");
      resetQuery();
    },
    /** 검색 결과에서 사진을 열었을 때. 검색 중이 아니면 아무것도 보내지 않는다. */
    trackResultOpened: (mediaId: number) => {
      if (!isSearching) return;
      const index = searchedItems.findIndex((item) => item.mediaId === mediaId);
      if (index === -1) return;
      track("Search Result Opened", {
        search_type: searchType,
        rank: index + 1,
        result_count: searchedItems.length,
      });
    },
  };
};
