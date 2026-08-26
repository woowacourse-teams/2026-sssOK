import { HiCheck, HiPlay } from "react-icons/hi2";

import type { MediaItem } from "../model/types";
import {
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
}

const formatDuration = (duration: number) => {
  const minutes = Math.floor(duration / 60);
  const seconds = duration % 60;

  return `${minutes}:${String(seconds).padStart(2, "0")}`;
};

export const MediaCard = ({ media, isMine, isSelected, onToggle }: MediaCardProps) => {
  const isVideo = media.type === "VIDEO";

  return (
    <CardButton type="button" $selected={isSelected} onClick={onToggle}>
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
      <SelectionMark $selected={isSelected}>{isSelected && <HiCheck />}</SelectionMark>
    </CardButton>
  );
};
