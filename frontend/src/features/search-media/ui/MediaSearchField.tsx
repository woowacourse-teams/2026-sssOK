import { useEffect, useRef, useState } from "react";
import { HiMagnifyingGlass, HiXMark } from "react-icons/hi2";

import { MAX_SEARCH_QUERY_LENGTH, normalizeSearchQuery } from "../model/searchQuery";
import {
  CloseButton,
  SEARCH_FIELD_MOTION_MS,
  SearchForm,
  SearchInput,
} from "./MediaSearchField.styles";

const prefersReducedMotion = () =>
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const CLOSE_FALLBACK_MARGIN_MS = 150;

interface MediaSearchFieldProps {
  onSearch: (query: string) => void;
  onClose: () => void;
}

// 검색창 필드
export const MediaSearchField = ({ onSearch, onClose }: MediaSearchFieldProps) => {
  const [draft, setDraft] = useState("");
  const [isClosing, setIsClosing] = useState(false);
  const closeTimerRef = useRef<number>(undefined);
  const hasClosedRef = useRef(false);

  useEffect(() => () => window.clearTimeout(closeTimerRef.current), []);

  const finishClose = () => {
    if (hasClosedRef.current) return;
    hasClosedRef.current = true;
    window.clearTimeout(closeTimerRef.current);
    onClose();
  };

  const close = () => {
    if (isClosing) return;
    setIsClosing(true);
    closeTimerRef.current = window.setTimeout(
      finishClose,
      prefersReducedMotion() ? 0 : SEARCH_FIELD_MOTION_MS + CLOSE_FALLBACK_MARGIN_MS,
    );
  };

  return (
    <SearchForm
      role="search"
      $closing={isClosing}
      onAnimationEnd={(event) => {
        if (isClosing && event.target === event.currentTarget) finishClose();
      }}
      onSubmit={(event) => {
        event.preventDefault();
        const query = normalizeSearchQuery(draft);
        if (query.length === 0) return;
        (document.activeElement as HTMLElement | null)?.blur();
        onSearch(query);
      }}
    >
      <HiMagnifyingGlass aria-hidden="true" />
      <SearchInput
        type="search"
        enterKeyHint="search"
        aria-label="사진 검색"
        placeholder="찾고 싶은 사진을 검색해주세요!"
        value={draft}
        maxLength={MAX_SEARCH_QUERY_LENGTH}
        autoFocus
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === "Escape") close();
        }}
      />
      <CloseButton type="button" aria-label="검색 닫기" onClick={close}>
        <HiXMark />
      </CloseButton>
    </SearchForm>
  );
};
