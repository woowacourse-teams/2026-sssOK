import { type ReactNode, useEffect, useRef, useState } from "react";
import {
  HiArrowLeft,
  HiArrowUpTray,
  HiChevronRight,
  HiMagnifyingGlass,
  HiSparkles,
  HiUsers,
  HiXMark,
} from "react-icons/hi2";

import { withSubjectParticle } from "@/shared/lib";

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
  ModeHead,
  Note,
  Panel,
  PanelTitle,
  Wrap,
} from "./SearchRequestField.styles";

type Mode = "keyword" | "uploader";

const MODES: Record<Mode, { label: string; icon: ReactNode; placeholder: string; guide: string }> =
  {
    keyword: {
      label: "사진 내용으로 찾기",
      icon: <HiMagnifyingGlass />,
      placeholder: "예: 바다에서 찍은 사진",
      guide: "찾고 싶은 사진 내용을 입력해 주세요",
    },
    uploader: {
      label: "올린 사람으로 찾기",
      icon: <HiArrowUpTray />,
      placeholder: "예: 소이",
      guide: "올린 사람의 이름을 입력해 주세요",
    },
  };

const PLACEHOLDER = "어떤 사진을 찾거나 정리할까요?";
const SELF_WORDS = ["나", "내"];

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
  /** 찾는 방법을 고르는 줄처럼 누른 뒤에도 패널을 열어 두는 줄. */
  keepOpen?: boolean;
  disabled?: boolean;
}

interface SearchRequestFieldProps {
  /** 결과를 보는 동안 입력창에 남겨 둘 글. 결과가 없으면 null 이다. */
  appliedText: string | null;
  folders: { id: number; name: string }[];
  uploaders: Uploader[];
  myNickname: string;
  onOpen?: () => void;
  onSearchKeyword: (query: string) => void;
  onSearchUploader: (uploader: Uploader) => void;
  onOpenFolder: (folderId: number) => void;
  /** 정리 방식 선택을 연다. */
  onOrganize?: () => void;
  /** 정리를 바라는 문장을 그대로 넘겨 바로 시작한다. 없으면 정리 방식 선택을 연다. */
  onOrganizeText?: (text: string) => void;
  /** 정리 중이라 새로 시작할 수 없는 까닭. 있으면 '자동으로 정리하기' 를 막고 이 글을 보여 준다. */
  organizeBlockedReason?: string | null;
  onClear: () => void;
}

// 갤러리 입력창
export const SearchRequestField = ({
  appliedText,
  folders,
  uploaders,
  myNickname,
  onOpen,
  onSearchKeyword,
  onSearchUploader,
  onOpenFolder,
  onOrganize,
  onOrganizeText,
  organizeBlockedReason = null,
  onClear,
}: SearchRequestFieldProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const [mode, setMode] = useState<Mode | null>(null);
  const [draft, setDraft] = useState("");
  const wrapRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const close = () => {
    setIsOpen(false);
    setMode(null);
    setDraft("");
  };

  useEffect(() => {
    if (!isOpen) return;
    const closeOutside = (event: PointerEvent) => {
      if (wrapRef.current?.contains(event.target as Node)) return;
      setIsOpen(false);
      setMode(null);
      setDraft("");
    };
    document.addEventListener("pointerdown", closeOutside);
    return () => document.removeEventListener("pointerdown", closeOutside);
  }, [isOpen]);

  const open = () => {
    if (isOpen) return;
    setIsOpen(true);
    onOpen?.();
  };

  const runAndClose = (run: () => void) => () => {
    run();
    close();
    inputRef.current?.blur();
  };

  const resolve = (name: string) => (name === "나" ? myNickname : name);
  const uploadedBy = (uploader: Uploader) =>
    `${uploader.isMe ? "내가" : withSubjectParticle(uploader.name)} 올린 사진 찾기`;

  const organizeAction: Action[] = [
    {
      key: "organize",
      icon: <HiSparkles />,
      label: "자동으로 정리하기",
      description: organizeBlockedReason ?? undefined,
      disabled: organizeBlockedReason !== null,
      run: () => onOrganize?.(),
    },
  ];
  const uploaderAction = (uploader: Uploader, description: string): Action => ({
    key: `uploader-${uploader.id}`,
    icon: <HiArrowUpTray />,
    label: uploadedBy(uploader),
    description,
    run: () => onSearchUploader(uploader),
  });
  const keywordAction = (query: string, description?: string): Action => ({
    key: "keyword",
    icon: <HiMagnifyingGlass />,
    label: `‘${query}’ 사진 찾기`,
    description,
    run: () => onSearchKeyword(query),
  });

  const content = ((): { title?: string; note?: string; actions: Action[] } => {
    const text = normalizeSearchQuery(draft);

    if (mode === "keyword") {
      return text ? { actions: [keywordAction(text)] } : { note: MODES.keyword.guide, actions: [] };
    }

    if (mode === "uploader") {
      if (!text) return { note: MODES.uploader.guide, actions: [] };
      const isSelf = SELF_WORDS.includes(text);
      const matches = uploaders.filter((uploader) =>
        isSelf ? uploader.isMe : uploader.name.toLowerCase().includes(text.toLowerCase()),
      );
      return matches.length
        ? { actions: matches.map((uploader) => uploaderAction(uploader, `${uploader.count}장`)) }
        : { note: `‘${text}’ 이름으로 올린 사진이 없어요`, actions: [] };
    }

    const request = parseRequest(text, [
      ...folders.map((folder) => folder.name),
      ...uploaders.map((uploader) => uploader.name),
      "나",
    ]);

    if (!request) {
      return {
        title: "원하는 걸 눌러 주세요",
        actions: [
          ...organizeAction,
          ...(Object.keys(MODES) as Mode[]).map((key) => ({
            key: `mode-${key}`,
            icon: MODES[key].icon,
            label: MODES[key].label,
            run: () => {
              setMode(key);
              inputRef.current?.focus();
            },
            keepOpen: true,
          })),
        ],
      };
    }

    switch (request.type) {
      case "organize":
        // 정리를 바라는 문장이면 시트를 거치지 않고 그 문장으로 바로 시작한다.
        return onOrganizeText
          ? {
              actions: organizeAction.map((action) => ({
                ...action,
                description: action.description ?? "요청한 대로 바로 시작",
                run: () => onOrganizeText(text),
              })),
            }
          : { actions: organizeAction };
      case "unsupported":
        return { note: "이 요청은 아직 못 해요.", actions: organizeAction };
      case "person": {
        const name = resolve(request.name);
        const folder = folders.find((candidate) => candidate.name === name);
        return folder
          ? {
              actions: [
                {
                  key: "folder",
                  icon: <HiUsers />,
                  label: `${name} 폴더 보기`,
                  description: "사람별 폴더로 이동",
                  run: () => onOpenFolder(folder.id),
                },
              ],
            }
          : // 폴더가 아직 없는 사람 이름은 사진 내용 검색으로 넘기지 않고 나누기를 먼저 권한다.
            { note: "사람별로 나눈 뒤에 찾을 수 있어요.", actions: organizeAction };
      }
      case "uploader": {
        const name = resolve(request.name);
        const uploader = uploaders.find((candidate) => candidate.name === name);
        return uploader
          ? { actions: [uploaderAction(uploader, "올린 사람으로 찾기")] }
          : { note: `‘${name}’ 이름으로 올린 사진이 없어요`, actions: [] };
      }
      case "keyword":
        return { actions: [keywordAction(request.query, "사진 내용으로 찾기")] };
    }
  })();

  const isActive = isOpen || appliedText !== null;

  return (
    <Wrap ref={wrapRef}>
      <Bar
        role="search"
        $active={isActive}
        onSubmit={(event) => {
          event.preventDefault();
          // 입력한 글에 맞는 줄이 바로 있을 때만 첫 줄을 실행한다.
          // 안내 문구 아래 줄은 다른 길을 권하는 것이라 Enter 로 실행하지 않는다.
          const [first] = content.actions;
          if (normalizeSearchQuery(draft) && !content.note && first && !first.disabled)
            runAndClose(first.run)();
        }}
      >
        <BarIcon $active={isActive} aria-hidden="true">
          {mode === "uploader" ? <HiArrowUpTray /> : <HiMagnifyingGlass />}
        </BarIcon>
        <Input
          ref={inputRef}
          type="search"
          enterKeyHint="search"
          aria-label="사진 찾기·정리 요청"
          aria-expanded={isOpen}
          aria-controls="search-request-panel"
          placeholder={mode ? MODES[mode].placeholder : PLACEHOLDER}
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
            aria-label="요청 닫기·검색어 지우기"
            onClick={() => {
              close();
              if (appliedText !== null) onClear();
            }}
          >
            <HiXMark />
          </IconAction>
        )}
      </Bar>

      {isOpen && (
        <Panel id="search-request-panel" aria-label="요청 패널" role="region">
          {mode && (
            <ModeHead>
              <IconAction
                type="button"
                aria-label="찾는 방법 다시 고르기"
                onClick={() => {
                  setMode(null);
                  setDraft("");
                  inputRef.current?.focus();
                }}
              >
                <HiArrowLeft />
              </IconAction>
              <p>{MODES[mode].label}</p>
            </ModeHead>
          )}
          {content.title && <PanelTitle>{content.title}</PanelTitle>}
          {content.note && <Note>{content.note}</Note>}
          {content.actions.map((action) => (
            <Item
              key={action.key}
              type="button"
              disabled={action.disabled}
              onClick={action.keepOpen ? action.run : runAndClose(action.run)}
            >
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
