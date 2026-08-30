import { HiCheck, HiPlay } from "react-icons/hi2";

import type { MediaItem } from "../model/types";
import {
  Card,
  CardButton,
  Duration,
  PlayMark,
  SelectionMark,
  Thumbnail,
  UploaderBadge,
} from "./MediaCard.styles";

interface MediaCardProps {
  media: MediaItem;
  isMine: boolean;
  isSelected: boolean;
  onToggle: () => void;
  onOpen?: () => void;
}

const formatDuration = (duration: number) => {
  const minutes = Math.floor(duration / 60);
  const seconds = duration % 60;

  return `${minutes}:${String(seconds).padStart(2, "0")}`;
};

export const MediaCard = ({ media, isMine, isSelected, onToggle, onOpen }: MediaCardProps) => {
  const isVideo = media.type === "VIDEO";

  return (
    <Card $selected={isSelected}>
      <CardButton
        type="button"
        onClick={onOpen ?? onToggle}
        aria-label={`${media.fileName} ${onOpen ? "크게 보기" : "선택하기"}`}
      >
        <Thumbnail src={media.thumbnailUrl} alt={media.fileName} loading="lazy" draggable={false} />

        {isVideo && (
          <>
            <PlayMark>
              <HiPlay />
            </PlayMark>
            {media.duration !== null && <Duration>{formatDuration(media.duration)}</Duration>}
          </>
        )}

        <UploaderBadge $mine={isMine}>{isMine ? "나" : media.uploaderName}</UploaderBadge>
      </CardButton>
      <SelectionMark
        type="button"
        $selected={isSelected}
        onClick={onToggle}
        aria-label={`${media.fileName} 선택`}
        aria-pressed={isSelected}
      >
        {isSelected && <HiCheck />}
      </SelectionMark>
    </Card>
  );
};
