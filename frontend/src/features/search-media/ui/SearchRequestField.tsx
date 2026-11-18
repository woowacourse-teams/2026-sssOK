import { type ReactNode, useEffect, useRef, useState } from "react";
import { HiArrowUpTray, HiChevronRight, HiMagnifyingGlass, HiXMark } from "react-icons/hi2";

import { withDirectionParticle, withSubjectParticle } from "@/shared/lib";

import { parseRequest } from "../model/parseRequest";
import { MAX_SEARCH_QUERY_LENGTH, normalizeSearchQuery } from "../model/searchQuery";
import {
  Arrow,
  Bar,
  BarIcon,
  Glyph,
  IconAction,
  Input,
  Item,
  Panel,
  Wrap,
} from "./SearchRequestField.styles";

const PLACEHOLDER = "사진 내용이나 이름으로 검색해 보세요";
const SELF_WORDS = ["나", "내"];
/** 이름 일부만 쳤을 때 먼저 보여 줄 사람 수. 사람이 많은 방에서도 패널이 길어지지 않게 한다. */
const MAX_UPLOADER_SUGGESTIONS = 3;

export interface Uploader {
  id: number;
  name: string;
  count: number;
  isMe: boolean;
}

interface Action {
  key: string;
  icon: ReactNode;
  label: string;
  description?: string;
  run: () => void;
}

interface SearchRequestFieldProps {
  /** 결과를 보는 동안 입력창에 남겨 둘 글. 결과가 없으면 null 이다. */
  appliedText: string | null;
  uploaders: Uploader[];
  onOpen?: () => void;
  onSearchKeyword: (query: string) => void;
  onSearchUploader: (uploader: Uploader) => void;
  onClear: () => void;
}

/** 입력한 글에 맞는 올린 사람. 글 속 이름을 먼저, 이름 일부가 맞는 사람을 그다음에 둔다. */
const matchUploaders = (text: string, uploaders: Uploader[]): Uploader[] => {
  const named = parseRequest(text, [...uploaders.map((uploader) => uploader.name), "나"])?.name;
  const lower = text.toLowerCase();

  const isMatch = (uploader: Uploader): boolean =>
    (uploader.isMe && (SELF_WORDS.includes(text) || named === "나")) ||
    uploader.name === named ||
    uploader.name.toLowerCase().includes(lower);

  return uploaders
    .filter(isMatch)
    .sort((a, b) => Number(b.name === named) - Number(a.name === named))
    .slice(0, MAX_UPLOADER_SUGGESTIONS);
};

// 갤러리 검색창
export const SearchRequestField = ({
  appliedText,
  uploaders,
  onOpen,
  onSearchKeyword,
  onSearchUploader,
  onClear,
}: SearchRequestFieldProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const [draft, setDraft] = useState("");
  const wrapRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const close = (): void => {
    setIsOpen(false);
    setDraft("");
  };

  useEffect(() => {
    if (!isOpen) return;
    const closeOutside = (event: PointerEvent): void => {
      if (wrapRef.current?.contains(event.target as Node)) return;
      setIsOpen(false);
      setDraft("");
    };
    document.addEventListener("pointerdown", closeOutside);
    return () => document.removeEventListener("pointerdown", closeOutside);
  }, [isOpen]);

  const open = (): void => {
    if (isOpen) return;
    setIsOpen(true);
    onOpen?.();
  };

  const runAndClose =
    (run: () => void): (() => void) =>
    () => {
      run();
      close();
      inputRef.current?.blur();
    };

  const text = normalizeSearchQuery(draft);
  // 글을 입력했을 때만 제안 줄을 보여 준다. 비어 있으면 패널을 그리지 않는다.
  const actions: Action[] = text
    ? [
        ...matchUploaders(text, uploaders).map((uploader) => ({
          key: `uploader-${uploader.id}`,
          icon: <HiArrowUpTray />,
          label: `${uploader.isMe ? "내가" : withSubjectParticle(uploader.name)} 올린 사진 보기`,
          description: `${uploader.count}장을 올렸어요`,
          run: () => onSearchUploader(uploader),
        })),
        {
          key: "keyword",
          icon: <HiMagnifyingGlass />,
          // 조사는 따옴표 밖에 붙인다: ‘민수’로, ‘바다 사진’으로
          label: `‘${text}’${withDirectionParticle(text).slice(text.length)} 검색하기`,
          description: "사진 내용으로 찾아요",
          run: () => onSearchKeyword(text),
        },
      ]
    : [];

  const isActive = isOpen || appliedText !== null;
  const isPanelOpen = isOpen && actions.length > 0;

  return (
    <Wrap ref={wrapRef}>
      <Bar
        role="search"
        $active={isActive}
        onSubmit={(event) => {
          event.preventDefault();
          const [first] = actions;
          if (first) runAndClose(first.run)();
        }}
      >
        <BarIcon $active={isActive} aria-hidden="true">
          <HiMagnifyingGlass />
        </BarIcon>
        <Input
          ref={inputRef}
          type="search"
          enterKeyHint="search"
          aria-label="사진 검색"
          aria-expanded={isPanelOpen}
          aria-controls="search-request-panel"
          placeholder={PLACEHOLDER}
          value={isOpen ? draft : (appliedText ?? "")}
          maxLength={MAX_SEARCH_QUERY_LENGTH}
          onFocus={open}
          onClick={open}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              close();
              inputRef.current?.blur();
            }
          }}
        />
        {isActive && (
          <IconAction
            type="button"
            aria-label="검색 닫기·검색어 지우기"
            onClick={() => {
              close();
              if (appliedText !== null) onClear();
            }}
          >
            <HiXMark />
          </IconAction>
        )}
      </Bar>

      {isPanelOpen && (
        <Panel id="search-request-panel" aria-label="검색 제안" role="region">
          {actions.map((action) => (
            <Item key={action.key} type="button" onClick={runAndClose(action.run)}>
              <Glyph aria-hidden="true">{action.icon}</Glyph>
              <span>
                <strong>{action.label}</strong>
                {action.description && <small>{action.description}</small>}
              </span>
              <Arrow aria-hidden="true">
                <HiChevronRight />
              </Arrow>
            </Item>
          ))}
        </Panel>
      )}
    </Wrap>
  );
};
