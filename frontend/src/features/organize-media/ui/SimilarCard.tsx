import { useRef, type FormEvent } from "react";

import chevronRight from "../assets/chevron-right.svg";
import { includedPhotos, type ReviewPerson } from "../model/personReview";
import { folderNameOf } from "../model/similarReview";
import { MAX_NAME_LENGTH } from "./PersonCard";
import { Card, Chevron, More, PhotoRow, Thumb, Thumbs, Who } from "./PersonReview.styles";
import { NameField, NameInput } from "./SimilarReview.styles";

/** 사진 줄에 보이는 칸 수. 더 있으면 마지막 칸 위에 남은 장수를 올린다. */
const VISIBLE_PHOTOS = 4;

interface SimilarCardProps {
  card: ReviewPerson;
  onOpenPhotos: () => void;
  onRename: (value: string) => void;
}

/**
 * 비슷한 사진 모음 카드. 위에서부터 사진 줄 → "어떤 사진인가요?" → 폴더 이름 입력칸 순서다 (AI-S2·S3).
 * 이름은 쓰는 대로 모음에 붙는다. 키보드의 완료는 폼 제출로 받아 입력칸에서 포커스만 뺀다.
 */
export const SimilarCard = ({ card, onOpenPhotos, onRename }: SimilarCardProps) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const photos = includedPhotos(card);
  const visible = photos.slice(0, VISIBLE_PHOTOS);
  const rest = photos.length - VISIBLE_PHOTOS;
  const folderName = folderNameOf(card);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    inputRef.current?.blur();
  };

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
        <NameInput
          ref={inputRef}
          aria-label="폴더 이름"
          placeholder="폴더 이름 입력"
          value={card.name ?? ""}
          maxLength={MAX_NAME_LENGTH}
          enterKeyHint="done"
          $filled={folderName !== null}
          onChange={(event) => onRename(event.target.value)}
        />
      </NameField>
    </Card>
  );
};
