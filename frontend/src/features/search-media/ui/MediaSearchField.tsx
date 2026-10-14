import { useEffect, useRef, useState } from "react";
import { HiCheck, HiChevronDown, HiXMark } from "react-icons/hi2";

import { DropdownMenu } from "@/shared/ui/dropdown-menu";

import { MAX_SEARCH_QUERY_LENGTH, normalizeSearchQuery } from "../model/searchQuery";
import type { SearchType } from "../model/searchType";
import {
  CloseButton,
  SEARCH_FIELD_MOTION_MS,
  SearchForm,
  SearchInput,
  SearchTypeButton,
  SearchTypeContainer,
  SearchTypeDivider,
  SearchTypeOption,
} from "./MediaSearchField.styles";

const prefersReducedMotion = () =>
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const CLOSE_FALLBACK_MARGIN_MS = 150;

const SEARCH_TYPES: Record<
  SearchType,
  { label: string; description: string; placeholder: string }
> = {
  keyword: {
    label: "검색어",
    description: "사진 내용으로 찾기",
    placeholder: "찾고 싶은 사진을 검색해주세요!",
  },
  nickname: {
    label: "닉네임",
    description: "올린 사람으로 찾기",
    placeholder: "닉네임을 입력해주세요!",
  },
};

const SEARCH_TYPE_ORDER: SearchType[] = ["keyword", "nickname"];

interface MediaSearchFieldProps {
  searchType: SearchType;
  onSearchTypeChange: (searchType: SearchType) => void;
  onSearch: (query: string) => void;
  onClose: () => void;
}

// 검색창 필드
export const MediaSearchField = ({
  searchType,
  onSearchTypeChange,
  onSearch,
  onClose,
}: MediaSearchFieldProps) => {
  const [draft, setDraft] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);
  const [isTypeMenuOpen, setIsTypeMenuOpen] = useState(false);
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

  const selectSearchType = (nextSearchType: SearchType) => {
    setIsTypeMenuOpen(false);
    if (nextSearchType !== searchType) {
      // 기준이 바뀌면 이전 기준으로 쓴 검색어는 의미가 없어서 비운다.
      setDraft("");
      onSearchTypeChange(nextSearchType);
    }
    inputRef.current?.focus();
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
      <SearchTypeContainer
        onKeyDown={(event) => {
          if (event.key === "Escape" && isTypeMenuOpen) setIsTypeMenuOpen(false);
        }}
      >
        <SearchTypeButton
          type="button"
          aria-label={`검색 기준: ${SEARCH_TYPES[searchType].label}`}
          aria-haspopup="menu"
          aria-expanded={isTypeMenuOpen}
          onClick={() => setIsTypeMenuOpen((isOpen) => !isOpen)}
        >
          {SEARCH_TYPES[searchType].label}
          <HiChevronDown aria-hidden="true" />
        </SearchTypeButton>
        {isTypeMenuOpen && (
          <DropdownMenu align="start" onClose={() => setIsTypeMenuOpen(false)}>
            <div role="menu" aria-label="검색 기준">
              {SEARCH_TYPE_ORDER.map((type) => (
                <SearchTypeOption
                  key={type}
                  role="menuitemradio"
                  aria-checked={type === searchType}
                  onClick={() => selectSearchType(type)}
                >
                  <span>
                    <strong>{SEARCH_TYPES[type].label}</strong>
                    <small>{SEARCH_TYPES[type].description}</small>
                  </span>
                  {type === searchType && <HiCheck aria-hidden="true" />}
                </SearchTypeOption>
              ))}
            </div>
          </DropdownMenu>
        )}
      </SearchTypeContainer>
      <SearchTypeDivider aria-hidden="true" />
      <SearchInput
        ref={inputRef}
        type="search"
        enterKeyHint="search"
        aria-label="사진 검색"
        placeholder={SEARCH_TYPES[searchType].placeholder}
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
