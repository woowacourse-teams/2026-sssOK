import { useRef, useState, type FormEvent } from "react";

import chevronRight from "../assets/chevron-right.svg";
import { includedPhotos, isEmptied, type ReviewPerson } from "../model/personReview";
import { EmptiedCard } from "./EmptiedCard";
import {
  Card,
  Chevron,
  Chip,
  ChipField,
  Chips,
  More,
  PhotoRow,
  RenameBox,
  RenameInput,
  RenameRow,
  RenameText,
  SmallAction,
  Thumb,
  Thumbs,
  Who,
} from "./PersonReview.styles";

/** 사진 줄에 보이는 칸 수. 더 있으면 마지막 칸 위에 남은 장수를 올린다. */
const VISIBLE_PHOTOS = 4;

/** 폴더 이름 규칙과 같다. 고른 이름이 그대로 폴더 이름이 된다. */
export const MAX_NAME_LENGTH = 12;

export interface NameChip {
  /** 폴더 이름이 될 값. */
  value: string;
  /** 칩에 보이는 글자. 내 이름은 "나" 로 보인다. */
  label: string;
}

interface PersonCardProps {
  person: ReviewPerson;
  chips: NameChip[];
  /** 고른 이름의 폴더가 방에 이미 있어 이름을 바꿔야 하는지. */
  needsRename: boolean;
  onOpenPhotos: () => void;
  onPickName: (name: string) => void;
  /** 직접 입력하거나 바꾼 이름. 늘 그 이름을 고른다. */
  onTypeName: (name: string) => void;
}

/**
 * 한 사람 카드. 위에서부터 사진 줄 → "누구인가요?" → 이름 칩 순서다 (AI-2·AI-3).
 * 고른 이름의 폴더가 이미 있으면 카드 아래에 이름을 바꾸라는 상자를 띄운다 (AI-3d·3e).
 */
export const PersonCard = ({
  person,
  chips,
  needsRename,
  onOpenPhotos,
  onPickName,
  onTypeName,
}: PersonCardProps) => {
  if (isEmptied(person)) return <EmptiedCard person={person} onOpenPhotos={onOpenPhotos} />;

  const photos = includedPhotos(person);
  const visible = photos.slice(0, VISIBLE_PHOTOS);
  const rest = photos.length - VISIBLE_PHOTOS;
  const label = person.name ?? "누구인가요?";

  return (
    <Card $picked={person.name !== null}>
      <PhotoRow
        type="button"
        aria-label={`${person.name ?? "이름 없는 사람"} 사진 ${photos.length}장 확인`}
        onClick={onOpenPhotos}
      >
        <Thumbs>
          {visible.map((photo, index) => (
            <Thumb key={photo.mediaId}>
              <img src={photo.thumbnailUrl} alt="" loading="lazy" />
              {index === VISIBLE_PHOTOS - 1 && rest > 0 && <More>+{rest}</More>}
            </Thumb>
          ))}
        </Thumbs>
        <Chevron aria-hidden="true">
          <img src={chevronRight} alt="" width={18} height={18} />
        </Chevron>
      </PhotoRow>

      <Who>{label}</Who>

      <Chips role="group" aria-label={`${label} 이름 고르기`}>
        {chips.map((chip) => (
          <Chip
            key={chip.value}
            type="button"
            $selected={person.name === chip.value}
            aria-pressed={person.name === chip.value}
            onClick={() => onPickName(chip.value)}
          >
            {chip.label}
          </Chip>
        ))}
        <TypeNameChip onSubmit={onTypeName} />
      </Chips>

      {needsRename && person.name !== null && (
        // 이름이 바뀌면 상자를 처음 상태로 되돌린다.
        <Rename key={person.name} name={person.name} onSubmit={onTypeName} />
      )}
    </Card>
  );
};

/**
 * '직접 입력' 칩. 칩 안에서 바로 쓴다.
 * 키보드의 엔터·완료는 폼 제출로 받는다. 모바일 한글 입력은 마지막 글자를 조합하는 중에 완료를 누르면
 * keydown 이 조합 중으로 오거나 키 이름 없이 와서 놓치기 쉽지만, 폼 제출은 조합이 끝난 뒤에 온다.
 * 제출하면 입력칸에서 포커스를 빼 키보드를 닫고, 그 blur 에서 이름을 고른다. 다른 곳을 눌러도 같다.
 */
const TypeNameChip = ({ onSubmit }: { onSubmit: (name: string) => void }) => {
  const [draft, setDraft] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const commit = (): void => {
    const name = draft.trim();
    if (!name) return;
    setDraft("");
    onSubmit(name);
  };

  const submit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    inputRef.current?.blur();
  };

  return (
    <ChipField onSubmit={submit}>
      <input
        ref={inputRef}
        aria-label="이름 직접 입력"
        placeholder="직접 입력"
        value={draft}
        maxLength={MAX_NAME_LENGTH}
        enterKeyHint="done"
        onChange={(event) => setDraft(event.target.value)}
        onBlur={commit}
      />
    </ChipField>
  );
};

/** 같은 이름 폴더가 이미 있을 때 이름을 바꾸게 하는 상자. */
const Rename = ({ name, onSubmit }: { name: string; onSubmit: (name: string) => void }) => {
  const [draft, setDraft] = useState<string | null>(null);
  const trimmed = draft?.trim() ?? "";

  if (draft === null) {
    return (
      <RenameBox>
        <RenameText>
          <strong>‘{name}’ 폴더가 이미 있어요.</strong> 다른 이름을 붙여 주세요.
        </RenameText>
        <SmallAction type="button" onClick={() => setDraft(name)}>
          이름 바꾸기
        </SmallAction>
      </RenameBox>
    );
  }

  const submit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    if (trimmed && trimmed !== name) onSubmit(trimmed);
  };

  return (
    <RenameBox>
      <RenameRow onSubmit={submit}>
        <RenameInput
          autoFocus
          aria-label="새 폴더 이름"
          value={draft}
          maxLength={MAX_NAME_LENGTH}
          onChange={(event) => setDraft(event.target.value)}
        />
        <SmallAction type="submit" disabled={!trimmed || trimmed === name}>
          이 이름으로
        </SmallAction>
      </RenameRow>
    </RenameBox>
  );
};
