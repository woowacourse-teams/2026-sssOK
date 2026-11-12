import { useState, type FormEvent } from "react";

import chevronRight from "../assets/chevron-right.svg";
import { includedPhotos, isEmptied, type ReviewPerson } from "../model/personReview";
import { folderNameOf } from "../model/similarReview";
import { MAX_NAME_LENGTH } from "./PersonCard";
import { Card, Chevron, More, PhotoRow, Thumb, Thumbs, Who } from "./PersonReview.styles";
import { EmptiedCard } from "./EmptiedCard";
import { ConflictNotice, NamedChip, NameField, NameInput } from "./SimilarReview.styles";

/** 사진 줄에 보이는 칸 수. 더 있으면 마지막 칸 위에 남은 장수를 올린다. */
const VISIBLE_PHOTOS = 4;

interface SimilarCardProps {
  card: ReviewPerson;
  /** 붙인 이름의 폴더가 방에 이미 있어 이름을 바꿔야 하는지 (AI-S3d). */
  needsRename?: boolean;
  onOpenPhotos: () => void;
  onRename: (value: string) => void;
  /** 이름을 다 쓰고 입력칸을 벗어났을 때. 다른 모음과 이름이 같은지는 부르는 쪽이 본다 (AI-S3b). */
  onCommit?: () => void;
}

/**
 * 비슷한 사진 모음 카드. 위에서부터 사진 줄 → "어떤 사진인가요?" → 폴더 이름 입력칸 순서다 (AI-S2·S3).
 * 이름은 쓰는 대로 모음에 붙는다. 키보드의 엔터·완료는 폼 제출로 받아 입력칸에서 포커스를 빼 키보드를 닫는다.
 * 이름이 있는 채로 입력칸을 벗어나면 주황 칩으로 바뀌어 이름이 들어갔다는 걸 보여 준다. 칩을 누르면 다시 고칠 수 있다.
 * 방에 이미 있는 폴더 이름이면 칩 대신 빨간 입력칸과 이름을 바꾸라는 상자를 그대로 둔다 (AI-S3d).
 */
export const SimilarCard = ({
  card,
  needsRename = false,
  onOpenPhotos,
  onRename,
  onCommit,
}: SimilarCardProps) => {
  const photos = includedPhotos(card);
  const visible = photos.slice(0, VISIBLE_PHOTOS);
  const rest = photos.length - VISIBLE_PHOTOS;
  const folderName = folderNameOf(card);
  const [isEditing, setIsEditing] = useState(false);
  const showsChip = folderName !== null && !isEditing && !needsRename;

  // 엔터·완료는 포커스만 뺀다. 칩으로 바꾸는 건 아래 blur 가 한다. 다른 곳을 눌러도 같다.
  const submit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    (event.currentTarget.elements.namedItem("folderName") as HTMLInputElement | null)?.blur();
  };

  if (isEmptied(card)) return <EmptiedCard person={card} onOpenPhotos={onOpenPhotos} />;

  return (
    <Card $picked={folderName !== null}>
      <PhotoRow
        type="button"
        aria-label={`${folderName ?? "이름 없는"} 모음 사진 ${photos.length}장 확인`}
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

      <Who>어떤 사진인가요?</Who>

      <NameField onSubmit={submit}>
        {showsChip ? (
          <NamedChip
            type="button"
            aria-label={`${folderName} 이름 고치기`}
            onClick={() => setIsEditing(true)}
          >
            {folderName}
          </NamedChip>
        ) : (
          <NameInput
            name="folderName"
            aria-label="폴더 이름"
            placeholder="폴더 이름 입력"
            value={card.name ?? ""}
            maxLength={MAX_NAME_LENGTH}
            enterKeyHint="done"
            // 칩을 눌러 고치러 왔을 때만 바로 키보드를 연다.
            autoFocus={isEditing}
            $filled={folderName !== null}
            $error={needsRename}
            aria-invalid={needsRename}
            onFocus={() => setIsEditing(true)}
            onBlur={() => {
              setIsEditing(false);
              onCommit?.();
            }}
            onChange={(event) => onRename(event.target.value)}
          />
        )}
      </NameField>

      {needsRename && folderName !== null && (
        <ConflictNotice role="alert">
          <strong>‘{folderName}’ 폴더가 이미 있어요.</strong> 다른 이름을 붙여 주세요.
        </ConflictNotice>
      )}
    </Card>
  );
};
